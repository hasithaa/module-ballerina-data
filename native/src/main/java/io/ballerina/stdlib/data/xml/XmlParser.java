package io.ballerina.stdlib.data.xml;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.utils.Constants;
import io.ballerina.stdlib.data.FromString;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

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
    private static Stack<Type> restType = new Stack<>();
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
            throw ErrorCreator.createError(StringUtils.fromString(PARSE_ERROR_PREFIX + e.getMessage()));
        }
    }

    private static void initRootObject(Type recordType) {
        if (recordType == null) {
            throw ErrorCreator.createError(StringUtils.fromString("expected record type for input type"));
        }
        currentNode = ValueCreator.createRecordValue((RecordType) recordType);
        nodesStack.push(currentNode);
    }

    private void handleXMLStreamException(Exception e) {
        String reason = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        if (reason == null) {
            throw ErrorCreator.createError(StringUtils.fromString(PARSE_ERROR));
        }
        throw ErrorCreator.createError(StringUtils.fromString(PARSE_ERROR_PREFIX + reason));
    }

    public Object parse(Type type) {
        if (type.getTag() != TypeTags.RECORD_TYPE_TAG) {
            throw ErrorCreator.createError(StringUtils.fromString("unsupported type"));
        }
        rootRecord = (RecordType) type;
        fieldHierarchy.push(new HashMap<>(rootRecord.getFields()));
        restType.push(rootRecord.getRestFieldType());
        initRootObject(rootRecord);
        return parse();
    }

    public Object parse() {
        try {
            ignoreRootElement(xmlStreamReader);

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

    private void ignoreRootElement(XMLStreamReader xmlStreamReader) {
        try {
            if (xmlStreamReader.hasNext() && xmlStreamReader.next() != START_ELEMENT) {
                throw ErrorCreator.createError(StringUtils.fromString("Invalid root must be an start element"));
            }
            rootElement = xmlStreamReader.getLocalName();

        } catch (XMLStreamException e) {
            handleXMLStreamException(e);
        }
    }

    private void readText(XMLStreamReader xmlStreamReader) {
        if (currentField == null) {
            return;
        }

        BString text = StringUtils.fromString(xmlStreamReader.getText());
        BString fieldName = StringUtils.fromString(currentField.getFieldName());
        Type fieldType = currentField.getFieldType();
        if (currentNode.containsKey(fieldName)) {
            // Handle - <name>James <!-- FirstName --> Clark</name>
            if (!siblings.get(currentField.getFieldName()) && fieldType.getTag() == TypeTags.STRING_TAG) {
                currentNode.put(fieldName, StringUtils.fromString(
                        String.valueOf(currentNode.get(fieldName)) + xmlStreamReader.getText()));
                return;
            }

            // TODO: Handle closed array compatible check and expected type check for array.
            if (currentField.getFieldType().getTag() != TypeTags.ARRAY_TAG) {
                throw ErrorCreator.createError(StringUtils.fromString("Incompatible type"));
            }
            ((BArray) currentNode.get(fieldName)).append(convertStringToExpType(text, fieldType));
            return;
        }
        currentNode.put(fieldName, convertStringToExpType(text, fieldType));
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
        return ErrorCreator.createError(StringUtils.fromString("Incompatible rest type"));
    }

    private Object buildDocument() {
        validateRequiredFields(siblings);
        return nodesStack.peek();
    }

    private void endElement(XMLStreamReader xmlStreamReader) {
        currentField = null;
        if (parents.isEmpty() || !parents.peek().containsKey(xmlStreamReader.getName().getLocalPart())) {
            return;
        }

        validateRequiredFields(siblings);
        currentNode = (BMap<BString, Object>) nodesStack.pop();
        siblings = parents.pop();
        fieldHierarchy.pop();
        restType.pop();
    }

    private void validateRequiredFields(LinkedHashMap<String, Boolean> siblings) {
        HashSet<String> siblingKeys = new HashSet<>(siblings.keySet());

        for (String key : fieldHierarchy.peek().keySet()) {
            if (!siblingKeys.contains(key)) {
                throw ErrorCreator.createError(StringUtils.fromString("Required field: " + key));
            }
        }
    }

    private void readElement(XMLStreamReader xmlStreamReader) {
        String elemName = xmlStreamReader.getName().getLocalPart();
        currentField = fieldHierarchy.peek().get(elemName);

        if (currentField == null) {
            // TODO: Check rest field type compatibility for record type (rest field)
            if (restType.peek() == null) {
                return;
            }

            Optional<String> lastElement = Optional.ofNullable(getLastElementInSiblings(siblings));
            String restStartPoint = lastElement.orElseGet(() -> rootElement);
            restFieldsPoints.push(restStartPoint);
            currentNode = (BMap<BString, Object>) parseRestField();
            return;
        }

        Object temp = currentNode.get(StringUtils.fromString(elemName));
        if (!siblings.containsKey(elemName)) {
            siblings.put(elemName, false);
        } else if (!(temp instanceof BArray)) {
            BArray tempArray = ValueCreator.createArrayValue(definedAnyDataArrayType);
            tempArray.append(temp);
            currentNode.put(StringUtils.fromString(elemName), tempArray);
        }

        Type fieldType = currentField.getFieldType();
        if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            parents.push(siblings);
            siblings = new LinkedHashMap<>();
            updateNextValue((RecordType) currentField.getFieldType(), elemName);
        } else if (fieldType.getTag() == TypeTags.ARRAY_TAG
                && ((ArrayType) fieldType).getElementType().getTag() == TypeTags.RECORD_TYPE_TAG) {
            parents.push(siblings);
            siblings = new LinkedHashMap<>();
            updateNextValue((RecordType) ((ArrayType) fieldType).getElementType(), elemName);
        }
    }

    public void updateNextValue(RecordType rootRecord, String elemName) {
        BMap<BString, Object> nextValue = ValueCreator.createRecordValue(rootRecord);
        fieldHierarchy.push(new HashMap<>(rootRecord.getFields()));
        restType.push(rootRecord.getRestFieldType());

        Object temp = currentNode.get(StringUtils.fromString(elemName));
        if (temp instanceof BArray) {
            ((BArray) temp).append(nextValue);
        } else {
            currentNode.put(StringUtils.fromString(elemName), nextValue);
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

                if (xmlStreamReader.hasNext()) {
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
        String fieldName = xmlStreamReader.getName().getLocalPart();
        if (siblings.containsKey(fieldName) && !siblings.get(fieldName)) {
            siblings.put(fieldName, true);
        }

        if (parents.isEmpty() || !parents.peek().containsKey(xmlStreamReader.getName().getLocalPart())) {
            return;
        }

        currentNode = (BMap<BString, Object>) nodesStack.pop();
        siblings = parents.pop();
        if (siblings.get(fieldName) && restFieldsPoints.contains(fieldName)) {
            restFieldsPoints.remove(fieldName);
        }
        siblings.put(fieldName, true);
    }

    private void readTextRest(XMLStreamReader xmlStreamReader, BString currentFieldName) {
        BString text = StringUtils.fromString(xmlStreamReader.getText());
        if (currentNode.get(currentFieldName) instanceof BArray) {
            ((BArray) currentNode.get(currentFieldName)).append(convertStringToRestExpType(text, restType.peek()));
        } else {
            currentNode.put(currentFieldName, convertStringToRestExpType(text, restType.peek()));
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

//    private String getXmlNameFromRecordAnnotation(RecordType recordType, String recordName) {
//        BMap<BString, Object> annotations = recordType.getAnnotations();
//        for (BString annotationsKey : annotations.getKeys()) {
//            String key = annotationsKey.getValue();
//            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAME)) {
//                return ((BMap<BString, Object>) annotations.get(annotationsKey)).get(Constants.VALUE).toString();
//            }
//        }
//        return recordName;
//    }
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
