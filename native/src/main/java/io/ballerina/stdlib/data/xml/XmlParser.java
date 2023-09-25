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
import io.ballerina.stdlib.data.FromString;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
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
//    private Map<String, String> namespaces; // xml ns declarations from Bal source [xmlns "http://ns.com" as ns]
    static BMap<BString, Object> currentNode;
    static Deque<Object> nodesStack = new ArrayDeque<>();
    static Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
    static Stack<Type> restType = new Stack<>();
    static RecordType rootRecord;
    static Field currentField;
    Stack<String> parent = new Stack<>();
    Deque<String> sibling = new ArrayDeque<>();
    Type definedJsonType = PredefinedTypes.TYPE_JSON;
    ArrayType definedJsonArrayType = TypeCreator.createArrayType(definedJsonType);
    public static final String PARSE_ERROR = "failed to parse xml";
    public static final String PARSE_ERROR_PREFIX = PARSE_ERROR + ": ";

    public XmlParser(String str) {
        this(new StringReader(str));
    }

    public XmlParser(Reader stringReader) {
//        namespaces = new HashMap<>();
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
//        } catch (DeferredParsingException e) {
//            throw ErrorCreator.createError(StringUtils.fromString(e.getCause().getMessage()));
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
            // Neglect root element
            if (xmlStreamReader.hasNext()) {
                xmlStreamReader.next();
            }

            while (xmlStreamReader.hasNext()) {
                int next = xmlStreamReader.next();
                switch (next) {
                    case START_ELEMENT:
                        readElement(xmlStreamReader);
                        break;
                    case END_ELEMENT:
                        endElement();
                        break;
                    case PROCESSING_INSTRUCTION:
                        readPI(xmlStreamReader);
                        break;
                    case COMMENT:
                        readComment(xmlStreamReader);
                        break;
                    case CDATA:
                    case CHARACTERS:
                        readText(xmlStreamReader);
                        break;
                    case END_DOCUMENT:
                        return buildDocument();
                    case DTD:
                        handleDTD(xmlStreamReader);
                        break;
                    default:
                        assert false;
                }
            }
        } catch (Exception e) {
            handleXMLStreamException(e);
        }

        return null;
    }

    private void handleDTD(XMLStreamReader xmlStreamReader) {
        // ignore
    }

    private void readPI(XMLStreamReader xmlStreamReader) {
       // ignore
    }

    private void readText(XMLStreamReader xmlStreamReader) {
        if (currentField == null) {
            return;
        }

        BString text = StringUtils.fromString(xmlStreamReader.getText());
        BString fieldName = StringUtils.fromString(currentField.getFieldName());
        Type fieldType = currentField.getFieldType();
        if (currentNode.containsKey(fieldName)) {
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

    private void readComment(XMLStreamReader xmlStreamReader) {
        // Ignore
    }

    private Object buildDocument() {
//        if (!fieldHierarchy.isEmpty()) {
//            throw ErrorCreator.createError(StringUtils.fromString("Incompatible type"));
//        }
        return nodesStack.peek();
    }

    private void endElement() {
        if (parent.contains(xmlStreamReader.getName().getLocalPart())) {
            currentNode = (BMap<BString, Object>) nodesStack.pop();
            sibling.clear();
            sibling.push(parent.pop());
            fieldHierarchy.pop();
            restType.pop();
        }
    }

    private void readElement(XMLStreamReader xmlStreamReader) {
        String elemName = xmlStreamReader.getName().getLocalPart();
        currentField = fieldHierarchy.peek().get(elemName);

        if (currentField == null) {
            // TODO: Check rest field type compatibility
            if (restType.peek() == null) {
                return;
            }
        }

        if (!sibling.contains(elemName)) {
            sibling.push(elemName);
        } else {
            Object temp = currentNode.get(StringUtils.fromString(elemName));
            BArray tempArray = ValueCreator.createArrayValue(definedJsonArrayType);
            tempArray.append(temp);
            currentNode.put(StringUtils.fromString(elemName), tempArray);
        }

        Type fieldType = currentField.getFieldType();
        if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            parent.push(sibling.remove());
            updateNextValue((RecordType) currentField.getFieldType(), elemName);
        } else if (fieldType.getTag() == TypeTags.ARRAY_TAG
                && ((ArrayType) fieldType).getElementType().getTag() == TypeTags.RECORD_TYPE_TAG) {
            parent.push(sibling.remove());
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
}
