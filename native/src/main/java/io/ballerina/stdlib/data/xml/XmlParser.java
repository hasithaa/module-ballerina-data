package io.ballerina.stdlib.data.xml;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.FromString;
import io.ballerina.stdlib.data.utils.Constants;
import io.ballerina.stdlib.data.utils.DataUtils;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.DTD;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 *
 *
 * @since 1.0.0
 */
public class XmlParser {

    // XMLInputFactory2
    private static final XMLInputFactory xmlInputFactory;

    static {
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    private XMLStreamReader xmlStreamReader;
    private static BMap<BString, Object> currentNode;
    private static Stack<Object> nodesStack = new Stack<>();
    private static Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
    private static Stack<Map<String, Field>> attributeHierarchy = new Stack<>();
    private static Stack<Map<String, String>> modifiedNamesHierarchy = new Stack<>();
    private static Stack<Type> restTypes = new Stack<>();
    private static Stack<String> restFieldsPoints = new Stack<>();
    private static RecordType rootRecord;
    private static Field currentField;
    private static String rootElement;
    private Stack<LinkedHashMap<String, Boolean>> parents = new Stack<>();
    private LinkedHashMap<String, Boolean> siblings = new LinkedHashMap<>();
    private ArrayType definedAnyDataArrayType = TypeCreator.createArrayType(PredefinedTypes.TYPE_ANYDATA);
    public static final String PARSE_ERROR = "failed to parse xml";
    public static final String PARSE_ERROR_PREFIX = PARSE_ERROR + ": ";

    public XmlParser(Reader stringReader) {
        try {
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(stringReader);
        } catch (XMLStreamException e) {
            handleXMLStreamException(e);
        }
    }

    public static Object parse(Reader reader, Type type) {
        try {
            XmlParser xmlParser = new XmlParser(reader);
            return xmlParser.parse(type);
        } catch (BError e) {
            throw e;
        } catch (Throwable e) {
            throw DataUtils.getXmlError(PARSE_ERROR_PREFIX + e.getMessage());
        }
    }

    private static void initRootObject(Type recordType) {
        if (recordType == null) {
            throw DataUtils.getXmlError("expected record type for input type");
        }
        currentNode = ValueCreator.createRecordValue((RecordType) recordType);
        nodesStack.push(currentNode);
    }

    private void handleXMLStreamException(Exception e) {
        String reason = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        if (reason == null) {
            throw DataUtils.getXmlError(PARSE_ERROR);
        }
        throw DataUtils.getXmlError(PARSE_ERROR_PREFIX + reason);
    }

    public Object parse(Type type) {
        if (type.getTag() != TypeTags.RECORD_TYPE_TAG) {
            throw DataUtils.getXmlError("unsupported type");
        }
        rootRecord = (RecordType) type;
        return parse();
    }

    public Object parse() {
        try {
            parseRootElement(xmlStreamReader);

            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();
                switch (next) {
                    case START_ELEMENT:
                        readElement(xmlStreamReader);
                        break;
                    case END_ELEMENT:
                        endElement(xmlStreamReader);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        readText(xmlStreamReader);
                        break;
                    case END_DOCUMENT:
                        return buildDocument();
                    case PROCESSING_INSTRUCTION:
                    case COMMENT:
                    case DTD:
                        // Ignore
                        break;
                    default:
                        assert false;
                }
            }
        } catch (Exception e) {
            handleXMLStreamException(e);
        }

        return currentNode;
    }

    private void parseRootElement(XMLStreamReader xmlStreamReader) {
        try {
            initRootObject(rootRecord);

            if (xmlStreamReader.hasNext() && xmlStreamReader.next() != START_ELEMENT) {
                throw DataUtils.getXmlError("Invalid root must be an start element");
            }

            rootElement = getXmlNameFromRecordAnnotation(rootRecord, rootRecord.getName());
            String elementName = xmlStreamReader.getLocalName();
            if (!rootElement.equals(elementName)) {
                throw DataUtils.getXmlError("The record type name: " + rootElement +
                        " mismatch with given XML name: " + elementName);
            }

            validateNamespace(xmlStreamReader, rootRecord);

            // Keep track of fields and attributes
            fieldHierarchy.push(new HashMap<>(getAllFieldsInRecordType(rootRecord)));
            restTypes.push(rootRecord.getRestFieldType());
            attributeHierarchy.push(new HashMap<>(getAllAttributesInRecordType(rootRecord)));
            handleAttributes(xmlStreamReader);

        } catch (XMLStreamException e) {
            handleXMLStreamException(e);
        }
    }

    private void readText(XMLStreamReader xmlStreamReader) {
        if (currentField == null) {
            Map<String, Field> currentFieldMap = fieldHierarchy.peek();
            if (currentFieldMap.containsKey(Constants.CONTENT)) {
                currentField = currentFieldMap.remove(Constants.CONTENT);
            } else {
                return;
            }
        }

        BString text = StringUtils.fromString(xmlStreamReader.getText());
        String fieldName = currentField.getFieldName();
        BString bFieldName = StringUtils.fromString(fieldName);
        Type fieldType = currentField.getFieldType();
        if (currentNode.containsKey(bFieldName)) {
            // Handle - <name>James <!-- FirstName --> Clark</name>
            if (!siblings.get(modifiedNamesHierarchy.peek().getOrDefault(fieldName, fieldName))
                    && fieldType.getTag() == TypeTags.STRING_TAG) {
                currentNode.put(bFieldName, StringUtils.fromString(
                        String.valueOf(currentNode.get(bFieldName)) + xmlStreamReader.getText()));
                return;
            }

            if (fieldType.getTag() != TypeTags.ARRAY_TAG) {
                throw DataUtils.getXmlError("Incompatible type");
            }

            int arraySize = ((ArrayType) fieldType).getSize();
            if (arraySize != -1 && arraySize <= ((BArray) currentNode.get(bFieldName)).getLength()) {
                return;
            }

            ((BArray) currentNode.get(bFieldName)).append(convertStringToExpType(text, fieldType));
            return;
        }

        if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            handleContentFieldInRecordType((RecordType) fieldType, text);
            return;
        } else if (fieldType.getTag() == TypeTags.ARRAY_TAG
                && ((ArrayType) fieldType).getElementType().getTag() == TypeTags.RECORD_TYPE_TAG) {
            handleContentFieldInRecordType((RecordType) ((ArrayType) fieldType).getElementType(), text);
            return;
        }
        currentNode.put(bFieldName, convertStringToExpType(text, fieldType));
    }

    private void handleContentFieldInRecordType(RecordType recordType, BString text) {
        fieldHierarchy.pop();
        restTypes.pop();
        modifiedNamesHierarchy.pop();
        attributeHierarchy.pop();
        siblings = parents.pop();

        for (String key : recordType.getFields().keySet()) {
            if (key.contains(Constants.CONTENT)) {
                currentNode.put(StringUtils.fromString(key),
                        convertStringToExpType(text, recordType.getFields().get(key).getFieldType()));
                currentNode = (BMap<BString, Object>) nodesStack.pop();
                return;
            }
        }

        Type restType = recordType.getRestFieldType();
        if (restType == null) {
            throw DataUtils.getXmlError("Cannot add " + Constants.CONTENT + " field for closed record");
        }

        currentNode.put(StringUtils.fromString(Constants.CONTENT), convertStringToRestExpType(text, restType));
        currentNode = (BMap<BString, Object>) nodesStack.pop();
    }

    private Object convertStringToExpType(BString value, Type expType) {
        if (expType.getTag() == TypeTags.ARRAY_TAG) {
            expType = ((ArrayType) expType).getElementType();
        }
        return FromString.fromStringWithTypeInternal(value, expType);
    }

    private Object convertStringToRestExpType(BString value, Type expType) {
        switch (expType.getTag()) {
            case TypeTags.INT_TAG:
            case TypeTags.FLOAT_TAG:
            case TypeTags.DECIMAL_TAG:
            case TypeTags.STRING_TAG:
            case TypeTags.BOOLEAN_TAG:
            case TypeTags.UNION_TAG:
                return convertStringToExpType(value, expType);
            case TypeTags.ANYDATA_TAG:
                return convertStringToExpType(value, PredefinedTypes.TYPE_STRING);
        }
        return DataUtils.getXmlError("Incompatible rest type");
    }

    private Object buildDocument() {
        validateRequiredFields(siblings);
        return nodesStack.peek();
    }

    private void endElement(XMLStreamReader xmlStreamReader) {
        currentField = null;
        String elemName = xmlStreamReader.getName().getLocalPart();
        if (siblings.containsKey(elemName) && !siblings.get(elemName)) {
            siblings.put(elemName, true);
        }
        if (parents.isEmpty() || !parents.peek().containsKey(elemName)) {
            return;
        }

        validateRequiredFields(siblings);
        currentNode = (BMap<BString, Object>) nodesStack.pop();
        siblings = parents.pop();
        fieldHierarchy.pop();
        modifiedNamesHierarchy.pop();
        restTypes.pop();
        // TODO: Remove if check
        if (!attributeHierarchy.isEmpty()) {
            attributeHierarchy.pop();
        }
    }

    private void validateRequiredFields(LinkedHashMap<String, Boolean> siblings) {
        HashSet<String> siblingKeys = new HashSet<>(siblings.keySet());

        for (String key : fieldHierarchy.peek().keySet()) {
            // Validate required array size
            if (fieldHierarchy.peek().get(key).getFieldType().getTag() == TypeTags.ARRAY_TAG) {
                ArrayType arrayType = (ArrayType) fieldHierarchy.peek().get(key).getFieldType();
                if (arrayType.getSize() != -1
                        && arrayType.getSize() != ((BArray) currentNode.get(StringUtils.fromString(key))).getLength()) {
                    throw DataUtils.getXmlError("Array size is not compatible with the expected size");
                }
            }

            if (!siblingKeys.contains(modifiedNamesHierarchy.peek().getOrDefault(key, key))) {
                throw DataUtils.getXmlError("Required field: " + key);
            }
        }
    }

    private void readElement(XMLStreamReader xmlStreamReader) {
        String elemName = xmlStreamReader.getName().getLocalPart();
        currentField = fieldHierarchy.peek().get(elemName);

        if (currentField == null) {
            // TODO: Check rest field type compatibility for record type (rest field)
            if (restTypes.peek() == null) {
                return;
            }

            Optional<String> lastElement = Optional.ofNullable(getLastElementInSiblings(parents.peek()));
            String restStartPoint = lastElement.orElseGet(() -> rootElement);
            restFieldsPoints.push(restStartPoint);
            currentNode = (BMap<BString, Object>) parseRestField();
            return;
        }

        String fieldName = currentField.getFieldName();
        Object temp = currentNode.get(StringUtils.fromString(fieldName));
        if (!siblings.containsKey(elemName)) {
            siblings.put(elemName, false);
        } else if (!(temp instanceof BArray)) {
            BArray tempArray = ValueCreator.createArrayValue(definedAnyDataArrayType);
            tempArray.append(temp);
            currentNode.put(StringUtils.fromString(fieldName), tempArray);
        }

        Type fieldType = currentField.getFieldType();
        if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            parents.push(siblings);
            siblings = new LinkedHashMap<>();
            updateNextValue((RecordType) fieldType, fieldName);
            attributeHierarchy.push(new HashMap<>(getAllAttributesInRecordType((RecordType) fieldType)));
            validateNamespace(xmlStreamReader, (RecordType) fieldType);
            handleAttributes(xmlStreamReader);
        } else if (fieldType.getTag() == TypeTags.ARRAY_TAG) {
            Type referredType = TypeUtils.getReferredType(((ArrayType) fieldType).getElementType());
            if (referredType.getTag() != TypeTags.RECORD_TYPE_TAG) {
                return;
            }
            parents.push(siblings);
            siblings = new LinkedHashMap<>();
            RecordType elemType = (RecordType) referredType;
            updateNextValue(elemType, fieldName);
            attributeHierarchy.push(new HashMap<>(getAllAttributesInRecordType(elemType)));
            validateNamespace(xmlStreamReader, elemType);
            handleAttributes(xmlStreamReader);
        }
    }

    public void updateNextValue(RecordType rootRecord, String fieldName) {
        BMap<BString, Object> nextValue = ValueCreator.createRecordValue(rootRecord);
        fieldHierarchy.push(new HashMap<>(getAllFieldsInRecordType(rootRecord)));
        restTypes.push(rootRecord.getRestFieldType());

        Object temp = currentNode.get(StringUtils.fromString(fieldName));
        if (temp instanceof BArray) {
            int arraySize = ((ArrayType) currentField.getFieldType()).getSize();
            if (arraySize > ((BArray) temp).getLength() || arraySize == -1) {
                ((BArray) temp).append(nextValue);
            }
        } else {
            currentNode.put(StringUtils.fromString(fieldName), nextValue);
        }
        nodesStack.push(currentNode);
        currentNode = nextValue;
    }

    private Object parseRestField() {
        int next = xmlStreamReader.getEventType();
        BString currentFieldName = null;
        try {
            while (!restFieldsPoints.isEmpty()) {
                switch (next) {
                    case START_ELEMENT:
                        currentFieldName = readElementRest(xmlStreamReader);
                        break;
                    case END_ELEMENT:
                        endElementRest(xmlStreamReader);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        readTextRest(xmlStreamReader, currentFieldName);
                        break;
                    case PROCESSING_INSTRUCTION:
                    case COMMENT:
                    case DTD:
                        // Ignore
                        break;
                }

                if (xmlStreamReader.hasNext() && !restFieldsPoints.isEmpty()) {
                    next = xmlStreamReader.next();
                } else {
                    break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

        return nodesStack.pop();
    }

    private BString readElementRest(XMLStreamReader xmlStreamReader) {
        String elemName = xmlStreamReader.getName().getLocalPart();
        BString currentFieldName = StringUtils.fromString(elemName);
        String lastElement = getLastElementInSiblings(siblings);

        if (!siblings.isEmpty() && lastElement != null && !siblings.getOrDefault(lastElement, true)) {
            parents.push(siblings);
            siblings = new LinkedHashMap<>();
            siblings.put(elemName, false);
            BMap<BString, Object> temp =
                    (BMap<BString, Object>) currentNode.get(StringUtils.fromString(lastElement));
            temp.put(currentFieldName, ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA));
            nodesStack.add(currentNode);
            currentNode = temp;
            return currentFieldName;
        } else if (!siblings.containsKey(elemName)) {
            siblings.put(elemName, false);
            currentNode.put(currentFieldName, ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA));
            nodesStack.add(currentNode);
            return currentFieldName;
        }

        parents.push(siblings);
        siblings = new LinkedHashMap<>();
        Object currentElement = currentNode.get(StringUtils.fromString(elemName));
        nodesStack.add(currentNode);

        if (currentElement instanceof BArray) {
            return currentFieldName;
        }

        BArray tempArray = ValueCreator.createArrayValue(definedAnyDataArrayType);
        tempArray.append(currentElement);
        currentNode.put(StringUtils.fromString(elemName), tempArray);
        if (!(currentElement instanceof BMap)) {
            return currentFieldName;
        }
        BMap<BString, Object> temp = ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA);
        tempArray.append(temp);
        currentNode = temp;
        return currentFieldName;
    }

    private void endElementRest(XMLStreamReader xmlStreamReader) {
        String elemName = xmlStreamReader.getName().getLocalPart();
        if (siblings.containsKey(elemName) && !siblings.get(elemName)) {
            siblings.put(elemName, true);
        }

        if (parents.isEmpty() || !parents.peek().containsKey(xmlStreamReader.getName().getLocalPart())) {
            return;
        }

        currentNode = (BMap<BString, Object>) nodesStack.pop();
        siblings = parents.pop();
        if (siblings.containsKey(elemName) && restFieldsPoints.remove(elemName)) {
            fieldHierarchy.pop();
            restTypes.pop();
        }
        siblings.put(elemName, true);
    }

    private void readTextRest(XMLStreamReader xmlStreamReader, BString currentFieldName) {
        BString text = StringUtils.fromString(xmlStreamReader.getText());
        if (currentNode.get(currentFieldName) instanceof BArray) {
            ((BArray) currentNode.get(currentFieldName)).append(convertStringToRestExpType(text, restTypes.peek()));
        } else {
            currentNode.put(currentFieldName, convertStringToRestExpType(text, restTypes.peek()));
        }
    }

    private String getLastElementInSiblings(LinkedHashMap<String, Boolean> siblings) {
        Object[] arr = siblings.keySet().toArray();
        String lastElement = null;
        if (arr.length != 0) {
            lastElement = (String) arr[arr.length - 1];
        }
        return lastElement;
    }

    private String getXmlNameFromRecordAnnotation(RecordType recordType, String recordName) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        for (BString annotationsKey : annotations.getKeys()) {
            String key = annotationsKey.getValue();
            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAME)) {
                return ((BMap<BString, Object>) annotations.get(annotationsKey)).get(Constants.VALUE).toString();
            }
        }
        return recordName;
    }

    private Map<String, Field> getAllAttributesInRecordType(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        Map<String, Field> attributes = new HashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (keyStr.contains(Constants.FIELD)) {
                String attributeName = keyStr.split("\\$field\\$\\.")[1].replaceAll("\\\\", "");
                Map<BString, Object> fieldAnnotation = (Map<BString, Object>) annotations.get(annotationKey);
                for (BString key : fieldAnnotation.keySet()) {
                    if (key.getValue().endsWith(Constants.ATTRIBUTE)) {
                        attributes.put(getModifiedName(fieldAnnotation, attributeName),
                                recordType.getFields().get(attributeName));
                    }
                }
            }
        }
        return attributes;
    }

    private Map<String, Field> getAllFieldsInRecordType(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        HashMap<String, String> modifiedNames = new LinkedHashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (keyStr.contains(Constants.FIELD)) {
                String elementName = keyStr.split("\\$field\\$\\.")[1].replaceAll("\\\\", "");
                Map<BString, Object> fieldAnnotation = (Map<BString, Object>) annotations.get(annotationKey);
                modifiedNames.put(elementName, getModifiedName(fieldAnnotation, elementName));
            }
        }

        Map<String, Field> fields = new HashMap<>();
        Map<String, Field> recordFields = recordType.getFields();
        for (String key : recordFields.keySet()) {
            fields.put(modifiedNames.getOrDefault(key, key), recordFields.get(key));
        }
        modifiedNamesHierarchy.add(modifiedNames);
        return fields;
    }

    private String getModifiedName(Map<BString, Object> fieldAnnotation, String attributeName) {
        for (BString key : fieldAnnotation.keySet()) {
            if (key.getValue().endsWith(Constants.NAME)) {
                return ((Map<BString, Object>) fieldAnnotation.get(key)).get(Constants.VALUE).toString();
            }
        }
        return attributeName;
    }

    private void validateNamespace(XMLStreamReader xmlStreamReader, RecordType recordType) {
        ArrayList<String> namespace = getNamespace(recordType);
        int xmlNSCount =  xmlStreamReader.getNamespaceCount();

        if (namespace.isEmpty()) {
            return;
        } else if (xmlNSCount == 0) {
            throw DataUtils.getXmlError("Namespace mismatch");
        }

        for (int i = 0; i < xmlNSCount; i++) {
            if (xmlStreamReader.getNamespacePrefix(i).equals(namespace.get(0))
                    && xmlStreamReader.getNamespaceURI(i).equals(namespace.get(1))) {
                return;
            }
        }
        throw DataUtils.getXmlError("Namespace mismatch");
    }

    private ArrayList<String> getNamespace(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        ArrayList<String> namespace = new ArrayList<>();
        for (BString annotationsKey : annotations.getKeys()) {
            String key = annotationsKey.getValue();
            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAME_SPACE)) {
                namespace.add(((BString) ((BMap<BString, Object>) annotations.get(
                        annotationsKey)).get(Constants.PREFIX)).getValue());
                namespace.add(((BString) ((BMap<BString, Object>) annotations.get(
                        annotationsKey)).get(Constants.URI)).getValue());
                return namespace;
            }
        }
        return namespace;
    }

    private void handleAttributes(XMLStreamReader xmlStreamReader) {
        for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
            QName qName = xmlStreamReader.getAttributeName(i);
            String prefix = qName.getPrefix();
            String attributeName = qName.getLocalPart();

            String fieldName = prefix.equals("") ? attributeName : prefix + ":" + attributeName;
            Field field = attributeHierarchy.peek().remove(fieldName);
            if (field == null) {
                field = fieldHierarchy.peek().remove(fieldName);
            } else {
                fieldHierarchy.peek().remove(fieldName);
            }

            if (field == null) {
                return;
            }

            currentNode.put(StringUtils.fromString(field.getFieldName()), convertStringToExpType(
                    StringUtils.fromString(xmlStreamReader.getAttributeValue(0)), field.getFieldType()));
        }
    }
}

// TODO: Fix below bugs
// 1.
// type RecB record {|
//     record {|
//         record {|
//             // record {|
//             //     int[] B;
//             // |}[] C;
//         |} B;
//     |} A;
// |};

// public function main() returns error? {
//     string xmlStr2 = "<Data><A><D><C><D>1</D><D>2</D></C><C><D>3</D><D>4</D></C></D></A></Data>";
//     RecB rec2 = check fromXmlStringWithType(xmlStr2);
//     io:println(rec2);
// }

// 2. Handle - Expected type field containing array type with element count like int[3];

//
