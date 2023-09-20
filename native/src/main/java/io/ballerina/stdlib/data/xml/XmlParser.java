package io.ballerina.stdlib.data.xml;

import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BXml;
import io.ballerina.runtime.api.values.BXmlItem;
import io.ballerina.runtime.api.values.BXmlQName;
import io.ballerina.runtime.api.values.BXmlSequence;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private Map<String, String> namespaces; // xml ns declarations from Bal source [xmlns "http://ns.com" as ns]
    private Deque<BXmlSequence> seqDeque;
    private Deque<List<BXml>> siblingDeque;
    static Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
    static Stack<Type> restType = new Stack<>();
    static RecordType rootRecord;
    public static final String PARSE_ERROR = "failed to parse xml";
    public static final String PARSE_ERROR_PREFIX = PARSE_ERROR + ": ";

    public XmlParser(String str) {
        this(new StringReader(str));
    }

    public XmlParser(Reader stringReader) {
        namespaces = new HashMap<>();
        seqDeque = new ArrayDeque<>();
        siblingDeque = new ArrayDeque<>();

        ArrayList<BXml> siblings = new ArrayList<>();
        siblingDeque.push(siblings);
        seqDeque.push(ValueCreator.createXmlSequence(siblings));

        try {
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(stringReader);
        } catch (XMLStreamException e) {
            handleXMLStreamException(e);
        }
    }

    public static BXml parse(Reader reader, Type type) {
        try {
//            if (type.getTag() == TypeTags.RECORD_TYPE_TAG) {
//                rootRecord = (RecordType) type;
//                fieldHierarchy.push(new HashMap<>(rootRecord.getFields()));
//                restType.push(rootRecord.getRestFieldType());
//            } else {
//                throw ErrorCreator.createError(StringUtils.fromString("unsupported type"));
//            }
            XmlParser xmlParser = new XmlParser(reader);
            return xmlParser.parse();
        } catch (BError e) {
            throw e;
//        } catch (DeferredParsingException e) {
//            throw ErrorCreator.createError(StringUtils.fromString(e.getCause().getMessage()));
        } catch (Throwable e) {
            throw ErrorCreator.createError(StringUtils.fromString(PARSE_ERROR_PREFIX + e.getMessage()));
        }
    }

    private void handleXMLStreamException(Exception e) {
        String reason = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        if (reason == null) {
            throw ErrorCreator.createError(StringUtils.fromString(PARSE_ERROR));
        }
        throw ErrorCreator.createError(StringUtils.fromString(PARSE_ERROR_PREFIX + reason));
    }

    public BXml parse() {
        try {
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
        BXml xmlItem = ValueCreator.createXmlProcessingInstruction(xmlStreamReader.getPITarget(),
                xmlStreamReader.getPIData());
        siblingDeque.peek().add(xmlItem);
    }

    private void readText(XMLStreamReader xmlStreamReader) {
        siblingDeque.peek().add(ValueCreator.createXmlText(xmlStreamReader.getText()));
    }

    private void readComment(XMLStreamReader xmlStreamReader) {
        BXml xmlComment = ValueCreator.createXmlComment(xmlStreamReader.getText());
        siblingDeque.peek().add(xmlComment);
    }

    private BXmlSequence buildDocument() {
        this.siblingDeque.pop();
        return this.seqDeque.pop();
    }

    private void endElement() {
        this.siblingDeque.pop();
        this.seqDeque.pop();
    }

    private void readElement(XMLStreamReader xmlStreamReader) {
        QName elemName = xmlStreamReader.getName();
//        if (fieldHierarchy.peek().remove(elemName.getLocalPart()) == null) {
//            return;
//        }

        BXmlQName name = ValueCreator.createXmlQName(elemName.getLocalPart(),
                elemName.getNamespaceURI(), elemName.getPrefix());
        BXmlItem xmlItem = (BXmlItem) ValueCreator.createXmlValue(name, name, null);

        seqDeque.push(xmlItem.getChildrenSeq());

        siblingDeque.peek().add(xmlItem);
        populateAttributeMap(xmlStreamReader, xmlItem, elemName);
        siblingDeque.push(xmlItem.getChildrenSeq().getChildrenList());
    }
    // need to duplicate the same in xmlItem.setAttribute

    // todo: need to write a comment explaining each step
    private void populateAttributeMap(XMLStreamReader xmlStreamReader, BXmlItem xmlItem, QName elemName) {
        BMap<BString, BString> attributesMap = xmlItem.getAttributesMap();
        Set<QName> usedNS = new HashSet<>(); // Track namespace prefixes found in this element.

        int count = xmlStreamReader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            QName attributeName = xmlStreamReader.getAttributeName(i);
            attributesMap.put(StringUtils.fromString(attributeName.toString()),
                    StringUtils.fromString(xmlStreamReader.getAttributeValue(i)));
            if (!attributeName.getPrefix().isEmpty()) {
                usedNS.add(attributeName);
            }
        }

        if (!elemName.getPrefix().isEmpty()) {
            usedNS.add(elemName);
        }
        for (QName qName : usedNS) {
            String prefix = qName.getPrefix();
            String namespaceURI = qName.getNamespaceURI();
            if (namespaceURI.isEmpty()) {
                namespaceURI = namespaces.getOrDefault(prefix, "");
            }

            BString xmlnsPrefix = StringUtils.fromString(BXmlItem.XMLNS_NS_URI_PREFIX + prefix);
            attributesMap.put(xmlnsPrefix, StringUtils.fromString(namespaceURI));
        }

        int namespaceCount = xmlStreamReader.getNamespaceCount();
        for (int i = 0; i < namespaceCount; i++) {
            String uri = xmlStreamReader.getNamespaceURI(i);
            String prefix = xmlStreamReader.getNamespacePrefix(i);
            if (prefix == null || prefix.isEmpty()) {
                attributesMap.put(BXmlItem.XMLNS_PREFIX, StringUtils.fromString(uri));
            } else {
                attributesMap.put(StringUtils.fromString(BXmlItem.XMLNS_NS_URI_PREFIX + prefix),
                        StringUtils.fromString(uri));
            }
        }
    }
}
