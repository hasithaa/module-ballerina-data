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
 * Convert Xml string to a ballerina record.
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
    private final ArrayType definedAnyDataArrayType = TypeCreator.createArrayType(PredefinedTypes.TYPE_ANYDATA);
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
            XmlParserData xmlParserData = new XmlParserData();
            XmlParser xmlParser = new XmlParser(reader);
            return xmlParser.parse(type, xmlParserData);
        } catch (BError e) {
            throw e;
        } catch (Throwable e) {
            throw DataUtils.getXmlError(PARSE_ERROR_PREFIX + e.getMessage());
        }
    }

    private static void initRootObject(Type recordType, XmlParserData xmlParserData) {
        if (recordType == null) {
            throw DataUtils.getXmlError("expected record type for input type");
        }
        currentNode = ValueCreator.createRecordValue((RecordType) recordType);
        xmlParserData.nodesStack.push(currentNode);
    }

    private void handleXMLStreamException(Exception e) {
        String reason = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        if (reason == null) {
            throw DataUtils.getXmlError(PARSE_ERROR);
        }
        throw DataUtils.getXmlError(PARSE_ERROR_PREFIX + reason);
    }

    public Object parse(Type type, XmlParserData xmlParserData) {
        if (type.getTag() != TypeTags.RECORD_TYPE_TAG) {
            throw DataUtils.getXmlError("unsupported type");
        }
        xmlParserData.rootRecord = (RecordType) type;
        return parse(xmlParserData);
    }

    public Object parse(XmlParserData xmlParserData) {
        try {
            parseRootElement(xmlStreamReader, xmlParserData);

            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();
                switch (next) {
                    case START_ELEMENT:
                        readElement(xmlStreamReader, xmlParserData);
                        break;
                    case END_ELEMENT:
                        endElement(xmlStreamReader, xmlParserData);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        readText(xmlStreamReader, xmlParserData);
                        break;
                    case END_DOCUMENT:
                        return buildDocument(xmlParserData);
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

    public Object parseRecordRest(XmlParserData xmlParserData, String startElementName) {
        try {
            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();

                // Terminate the record rest field parsing if the end element is reached.
                if (next == END_ELEMENT) {
                    QName startElement = xmlStreamReader.getName();
                    if (startElement.getLocalPart().equals(startElementName)) {
                        xmlParserData.fieldHierarchy.pop();
                        xmlParserData.restTypes.pop();
                        break;
                    }
                }

                switch (next) {
                    case START_ELEMENT:
                        readElement(xmlStreamReader, xmlParserData);
                        break;
                    case END_ELEMENT:
                        endElement(xmlStreamReader, xmlParserData);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        readText(xmlStreamReader, xmlParserData);
                        break;
                    case END_DOCUMENT:
                        return buildDocument(xmlParserData);
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

    private void parseRootElement(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        try {
            RecordType rootRecord = xmlParserData.rootRecord;
            initRootObject(rootRecord, xmlParserData);

            if (xmlStreamReader.hasNext() && xmlStreamReader.next() != START_ELEMENT) {
                throw DataUtils.getXmlError("Invalid root must be an start element");
            }

            String elementName = getElementName(xmlStreamReader);
            xmlParserData.rootElement =
                    validateAndGetXmlNameFromRecordAnnotation(rootRecord, rootRecord.getName(), elementName);

            validateNamespace(xmlStreamReader, rootRecord);

            // Keep track of fields and attributes
            xmlParserData.fieldHierarchy.push(new HashMap<>(getAllFieldsInRecordType(rootRecord, xmlParserData)));
            xmlParserData.restTypes.push(rootRecord.getRestFieldType());
            xmlParserData.attributeHierarchy.push(new HashMap<>(getAllAttributesInRecordType(rootRecord)));
            handleAttributes(xmlStreamReader, xmlParserData);
        } catch (XMLStreamException e) {
            handleXMLStreamException(e);
        }
    }

    private void readText(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        Field currentField = xmlParserData.currentField;
        String text = xmlStreamReader.getText();
        if (text.strip().isBlank()) {
            return;
        }

        if (currentField == null) {
            Map<String, Field> currentFieldMap = xmlParserData.fieldHierarchy.peek();
            if (currentFieldMap.containsKey(Constants.CONTENT)) {
                currentField = currentFieldMap.remove(Constants.CONTENT);
            } else {
                return;
            }
        }

        BString bText = StringUtils.fromString(text);
        String fieldName = currentField.getFieldName();
        BString bFieldName = StringUtils.fromString(fieldName);
        Type fieldType = currentField.getFieldType();
        if (currentNode.containsKey(bFieldName)) {
            // Handle - <name>James <!-- FirstName --> Clark</name>
            if (!xmlParserData.siblings.get(
                    xmlParserData.modifiedNamesHierarchy.peek().getOrDefault(fieldName, fieldName))
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

            ((BArray) currentNode.get(bFieldName)).append(convertStringToExpType(bText, fieldType));
            return;
        }

        if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            handleContentFieldInRecordType((RecordType) fieldType, bText, xmlParserData);
            return;
        } else if (fieldType.getTag() == TypeTags.ARRAY_TAG
                && ((ArrayType) fieldType).getElementType().getTag() == TypeTags.RECORD_TYPE_TAG) {
            handleContentFieldInRecordType((RecordType) ((ArrayType) fieldType).getElementType(), bText, xmlParserData);
            return;
        }
        currentNode.put(bFieldName, convertStringToExpType(bText, fieldType));
    }

    private void handleContentFieldInRecordType(RecordType recordType, BString text, XmlParserData xmlParserData) {
        xmlParserData.fieldHierarchy.pop();
        xmlParserData.restTypes.pop();
        xmlParserData.modifiedNamesHierarchy.pop();
        xmlParserData.attributeHierarchy.pop();
        xmlParserData.siblings = xmlParserData.parents.pop();

        for (String key : recordType.getFields().keySet()) {
            if (key.contains(Constants.CONTENT)) {
                currentNode.put(StringUtils.fromString(key),
                        convertStringToExpType(text, recordType.getFields().get(key).getFieldType()));
                currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
                return;
            }
        }

        Type restType = recordType.getRestFieldType();
        if (restType == null) {
            throw DataUtils.getXmlError("Cannot add " + Constants.CONTENT + " field for closed record");
        }

        currentNode.put(StringUtils.fromString(Constants.CONTENT), convertStringToRestExpType(text, restType));
        currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
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

    private Object buildDocument(XmlParserData xmlParserData) {
        validateRequiredFields(xmlParserData.siblings, xmlParserData);
        return xmlParserData.nodesStack.peek();
    }

    private void endElement(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        xmlParserData.currentField = null;
        String elemName = getElementName(xmlStreamReader);
//        String elemName = xmlStreamReader.getName().getLocalPart();
        LinkedHashMap<String, Boolean> siblings = xmlParserData.siblings;
        Stack<LinkedHashMap<String, Boolean>> parents = xmlParserData.parents;
        if (siblings.containsKey(elemName) && !siblings.get(elemName)) {
            siblings.put(elemName, true);
        }
        if (parents.isEmpty() || !parents.peek().containsKey(elemName)) {
            return;
        }

        validateRequiredFields(siblings, xmlParserData);
        currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
        xmlParserData.siblings = parents.pop();
        xmlParserData.fieldHierarchy.pop();
        xmlParserData.modifiedNamesHierarchy.pop();
        xmlParserData.restTypes.pop();
        // TODO: Remove if check
        if (!xmlParserData.attributeHierarchy.isEmpty()) {
            xmlParserData.attributeHierarchy.pop();
        }
    }

    private void validateRequiredFields(LinkedHashMap<String, Boolean> siblings, XmlParserData xmlParserData) {
        HashSet<String> siblingKeys = new HashSet<>(siblings.keySet());

        for (String key : xmlParserData.fieldHierarchy.peek().keySet()) {
            // Validate required array size
            if (xmlParserData.fieldHierarchy.peek().get(key).getFieldType().getTag() == TypeTags.ARRAY_TAG) {
                ArrayType arrayType = (ArrayType) xmlParserData.fieldHierarchy.peek().get(key).getFieldType();
                if (arrayType.getSize() != -1
                        && arrayType.getSize() != ((BArray) currentNode.get(StringUtils.fromString(key))).getLength()) {
                    throw DataUtils.getXmlError("Array size is not compatible with the expected size");
                }
            }

            if (!siblingKeys.contains(xmlParserData.modifiedNamesHierarchy.peek().getOrDefault(key, key))) {
                throw DataUtils.getXmlError("Required field: " + key);
            }
        }
    }

    private void readElement(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        String elemName = getElementName(xmlStreamReader);
//        String elemName = xmlStreamReader.getName().getLocalPart();
        xmlParserData.currentField = xmlParserData.fieldHierarchy.peek().get(elemName);

        if (xmlParserData.currentField == null) {
            if (xmlParserData.restTypes.peek() == null) {
                return;
            }
            Optional<String> lastElement;
            if (xmlParserData.parents.isEmpty()) {
                lastElement = Optional.empty();
            } else {
                lastElement = Optional.ofNullable(getLastElementInSiblings(xmlParserData.parents.peek()));
            }
            String restStartPoint = lastElement.orElseGet(() -> xmlParserData.rootElement);
            xmlParserData.restFieldsPoints.push(restStartPoint);
            currentNode = (BMap<BString, Object>) parseRestField(xmlParserData);
            return;
        }

        Field currentField = xmlParserData.currentField;
        String fieldName = currentField.getFieldName();
        Object temp = currentNode.get(StringUtils.fromString(fieldName));
        if (!xmlParserData.siblings.containsKey(elemName)) {
            xmlParserData.siblings.put(elemName, false);
        } else if (!(temp instanceof BArray)) {
            BArray tempArray = ValueCreator.createArrayValue(definedAnyDataArrayType);
            tempArray.append(temp);
            currentNode.put(StringUtils.fromString(fieldName), tempArray);
        }

        Type fieldType = currentField.getFieldType();
        if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            xmlParserData.parents.push(xmlParserData.siblings);
            xmlParserData.siblings = new LinkedHashMap<>();
            updateNextValue((RecordType) fieldType, fieldName, fieldType, xmlParserData);
            xmlParserData.attributeHierarchy.push(new HashMap<>(getAllAttributesInRecordType((RecordType) fieldType)));
            validateNamespace(xmlStreamReader, (RecordType) fieldType);
            handleAttributes(xmlStreamReader, xmlParserData);
        } else if (fieldType.getTag() == TypeTags.ARRAY_TAG) {
            Type referredType = TypeUtils.getReferredType(((ArrayType) fieldType).getElementType());
            if (!currentNode.containsKey(StringUtils.fromString(fieldName))) {
                currentNode.put(StringUtils.fromString(fieldName),
                        ValueCreator.createArrayValue(definedAnyDataArrayType));
            }

            if (referredType.getTag() != TypeTags.RECORD_TYPE_TAG) {
                return;
            }
            xmlParserData.parents.push(xmlParserData.siblings);
            xmlParserData.siblings = new LinkedHashMap<>();
            RecordType elemType = (RecordType) referredType;
            updateNextValue(elemType, fieldName, fieldType, xmlParserData);
            xmlParserData.attributeHierarchy.push(new HashMap<>(getAllAttributesInRecordType(elemType)));
            validateNamespace(xmlStreamReader, elemType);
            handleAttributes(xmlStreamReader, xmlParserData);
        }
    }

    public void updateNextValue(RecordType rootRecord, String fieldName, Type fieldType, XmlParserData xmlParserData) {
        BMap<BString, Object> nextValue = ValueCreator.createRecordValue(rootRecord);
        xmlParserData.fieldHierarchy.push(new HashMap<>(getAllFieldsInRecordType(rootRecord, xmlParserData)));
        xmlParserData.restTypes.push(rootRecord.getRestFieldType());

        Object temp = currentNode.get(StringUtils.fromString(fieldName));
        if (temp instanceof BArray) {
            int arraySize = ((ArrayType) fieldType).getSize();
            if (arraySize > ((BArray) temp).getLength() || arraySize == -1) {
                ((BArray) temp).append(nextValue);
            }
        } else {
            currentNode.put(StringUtils.fromString(fieldName), nextValue);
        }
        xmlParserData.nodesStack.push(currentNode);
        currentNode = nextValue;
    }

    private Object parseRestField(XmlParserData xmlParserData) {
        int next = xmlStreamReader.getEventType();
        Optional<Object> restNode = handleRecordRestType(xmlParserData, xmlStreamReader);
        if (restNode.isPresent()) {
            return restNode.get();
        }

        BString currentFieldName = null;
        try {
            while (!xmlParserData.restFieldsPoints.isEmpty()) {
                switch (next) {
                    case START_ELEMENT:
                        currentFieldName = readElementRest(xmlStreamReader, xmlParserData);
                        break;
                    case END_ELEMENT:
                        endElementRest(xmlStreamReader, xmlParserData);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        readTextRest(xmlStreamReader, currentFieldName, xmlParserData);
                        break;
                    case PROCESSING_INSTRUCTION:
                    case COMMENT:
                    case DTD:
                        // Ignore
                        break;
                }

                if (xmlStreamReader.hasNext() && !xmlParserData.restFieldsPoints.isEmpty()) {
                    next = xmlStreamReader.next();
                } else {
                    break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

        return xmlParserData.nodesStack.pop();
    }

    private BString readElementRest(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        String elemName = getElementName(xmlStreamReader);
//        String elemName = xmlStreamReader.getName().getLocalPart();
        BString currentFieldName = StringUtils.fromString(elemName);
        String lastElement = getLastElementInSiblings(xmlParserData.siblings);

        if (!xmlParserData.siblings.isEmpty() && lastElement != null
                && !xmlParserData.siblings.getOrDefault(lastElement, true)) {
            xmlParserData.parents.push(xmlParserData.siblings);
            xmlParserData.siblings = new LinkedHashMap<>();
            xmlParserData.siblings.put(elemName, false);
            BMap<BString, Object> temp =
                    (BMap<BString, Object>) currentNode.get(StringUtils.fromString(lastElement));
            temp.put(currentFieldName, ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA));
            xmlParserData.nodesStack.add(currentNode);
            currentNode = temp;
            return currentFieldName;
        } else if (!xmlParserData.siblings.containsKey(elemName)) {
            xmlParserData.siblings.put(elemName, false);
            currentNode.put(currentFieldName, ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA));
            xmlParserData.nodesStack.add(currentNode);
            return currentFieldName;
        }

        xmlParserData.parents.push(xmlParserData.siblings);
        xmlParserData.siblings = new LinkedHashMap<>();
        Object currentElement = currentNode.get(StringUtils.fromString(elemName));
        xmlParserData.nodesStack.add(currentNode);

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

    private void endElementRest(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        String elemName = getElementName(xmlStreamReader);
//        String elemName = xmlStreamReader.getName().getLocalPart();
        if (xmlParserData.siblings.containsKey(elemName) && !xmlParserData.siblings.get(elemName)) {
            xmlParserData.siblings.put(elemName, true);
        }

        if (xmlParserData.parents.isEmpty()
                || !xmlParserData.parents.peek().containsKey(elemName)) {
            return;
        }

        currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
        xmlParserData.siblings = xmlParserData.parents.pop();
        if (xmlParserData.siblings.containsKey(elemName) && xmlParserData.restFieldsPoints.remove(elemName)) {
            xmlParserData.fieldHierarchy.pop();
            xmlParserData.restTypes.pop();
        }
        xmlParserData.siblings.put(elemName, true);
    }

    private void readTextRest(XMLStreamReader xmlStreamReader, BString currentFieldName, XmlParserData xmlParserData) {
        String text = xmlStreamReader.getText();
        if (text.strip().isBlank()) {
            return;
        }

        BString bText = StringUtils.fromString(text);
        if (currentNode.get(currentFieldName) instanceof BArray) {
            ((BArray) currentNode.get(currentFieldName)).append(
                    convertStringToRestExpType(bText, xmlParserData.restTypes.peek()));
        } else {
            currentNode.put(currentFieldName, convertStringToRestExpType(bText, xmlParserData.restTypes.peek()));
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

    private String validateAndGetXmlNameFromRecordAnnotation(RecordType recordType, String recordName,
                                                             String elementName) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        for (BString annotationsKey : annotations.getKeys()) {
            String key = annotationsKey.getValue();
            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAME)) {
                String namespace =
                        ((BMap<BString, Object>) annotations.get(annotationsKey)).get(Constants.VALUE).toString();
                if (!namespace.equals(elementName)) {
                    throw DataUtils.getXmlError("The record type name: " + namespace +
                            " mismatch with given XML name: " + elementName);
                }
                return namespace;
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

    private Map<String, Field> getAllFieldsInRecordType(RecordType recordType, XmlParserData xmlParserData) {
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
        xmlParserData.modifiedNamesHierarchy.add(modifiedNames);
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

    private void handleAttributes(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
            QName qName = xmlStreamReader.getAttributeName(i);
            String prefix = qName.getPrefix();
            String attributeName = qName.getLocalPart();

            String fieldName = prefix.equals("") ? attributeName : prefix + ":" + attributeName;
            Field field = xmlParserData.attributeHierarchy.peek().remove(fieldName);
            if (field == null) {
                field = xmlParserData.fieldHierarchy.peek().remove(fieldName);
            } else {
                xmlParserData.fieldHierarchy.peek().remove(fieldName);
            }

            if (field == null) {
                return;
            }

            currentNode.put(StringUtils.fromString(field.getFieldName()), convertStringToExpType(
                    StringUtils.fromString(xmlStreamReader.getAttributeValue(0)), field.getFieldType()));
        }
    }

    private Optional<Object> handleRecordRestType(XmlParserData xmlParserData, XMLStreamReader xmlStreamReader) {
        xmlParserData.currentField = null;
        Type restType = xmlParserData.restTypes.peek();
        int restTypeTag = restType.getTag();
        String elementName = getElementName(xmlStreamReader);
        if (restTypeTag == TypeTags.RECORD_TYPE_TAG) {
            RecordType recordType = (RecordType) restType;
            updateNextValue(recordType, elementName, restType, xmlParserData);
            parseRecordRest(xmlParserData, elementName);
            xmlParserData.siblings.clear();
            return Optional.ofNullable(xmlParserData.nodesStack.pop());
        } else if (restTypeTag == TypeTags.ARRAY_TAG) {
            ArrayType arrayType = (ArrayType) restType;
            Type elemType = TypeUtils.getReferredType(arrayType.getElementType());
            if (elemType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                // Create an array value since expected type is an array.
                if (!currentNode.containsKey(StringUtils.fromString(elementName))) {
                    currentNode.put(StringUtils.fromString(elementName),
                            ValueCreator.createArrayValue(definedAnyDataArrayType));
                }
                updateNextValue((RecordType) elemType, elementName, arrayType, xmlParserData);
                parseRecordRest(xmlParserData, elementName);
                xmlParserData.siblings.clear();
                return Optional.ofNullable(xmlParserData.nodesStack.pop());
            }
        }
        return Optional.empty();
    }

    private String getElementName(XMLStreamReader xmlStreamReader) {
        QName qName = xmlStreamReader.getName();
        String prefix = qName.getPrefix();
        String attributeName = qName.getLocalPart();
        return prefix.equals("") ? attributeName : prefix + ":" + attributeName;
    }

    static class XmlParserData {
        private Stack<Object> nodesStack = new Stack<>();
        private Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
        private Stack<Map<String, Field>> attributeHierarchy = new Stack<>();
        private Stack<Map<String, String>> modifiedNamesHierarchy = new Stack<>();
        private Stack<Type> restTypes = new Stack<>();
        private Stack<String> restFieldsPoints = new Stack<>();
        private RecordType rootRecord;
        private Field currentField;
        private String rootElement;
        private Stack<LinkedHashMap<String, Boolean>> parents = new Stack<>();
        private LinkedHashMap<String, Boolean> siblings = new LinkedHashMap<>();
    }
}

// TODO: Fix the below bug
