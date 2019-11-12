package me.markrose.xml.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Implements unit tests of {@link IncludeAwareEventReader}. The strategy
 * is to use an include-aware parser, the DOM parser, to read the XML file
 * and write the result document, after inclusions, to a temporary file.
 * Then both the temporary file and the original file are parsed using the
 * StAX parser, with the inclusion-aware event reader, and the sequence of
 * parsing events is compared to ensure they are equal. Processing instruction
 * and character events containing only whitespace are ignored, since the
 * extra &lt;xi:include&gt; tags and inclusions may introduce more ignorable
 * whitespace, and the included files may have additional processing instructions.
 */
public class IncludeAwareEventReaderTest {

    XMLInputFactory xmlInputFactory;
    private DocumentBuilderFactory documentBuilderFactory;
    private TransformerFactory transformerFactory;
    
    @Before
    public void setup() {
        xmlInputFactory = XMLInputFactory.newFactory();
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setXIncludeAware(true);
        transformerFactory = TransformerFactory.newInstance();
    }
    
    /**
     * Tests parsing a simple XML file with no includes.
     * 
     * @throws ParserConfigurationException if there is a problem using the DOM parser
     * @throws SAXException if there is a problem parsing using the DOM parser
     * @throws IOException if the XML file cannot be found
     * @throws TransformerException if the XML file, after includes, cannot be written
     * @throws XMLStreamException if there is a problem parsing using the StAX parser
     */
    @Test
    public void testNoIncludes() throws ParserConfigurationException, SAXException, IOException,
        TransformerException, XMLStreamException {
        
        checkSameEvents(new File("src/test/resources/no-include.xml"));
    }
    
    /**
     * Tests parsing an XML file with an include from the same directory.
     * 
     * @throws ParserConfigurationException if there is a problem using the DOM parser
     * @throws SAXException if there is a problem parsing using the DOM parser
     * @throws IOException if the XML file cannot be found
     * @throws TransformerException if the XML file, after includes, cannot be written
     * @throws XMLStreamException if there is a problem parsing using the StAX parser
     */
    @Test
    public void testSimpleInclude() throws ParserConfigurationException, SAXException, IOException,
        TransformerException, XMLStreamException {
        
        checkSameEvents(new File("src/test/resources/simple-include.xml"));
    }
    
    /**
     * Tests parsing an XML file with an include from a subdirectory.
     * 
     * @throws ParserConfigurationException if there is a problem using the DOM parser
     * @throws SAXException if there is a problem parsing using the DOM parser
     * @throws IOException if the XML file cannot be found
     * @throws TransformerException if the XML file, after includes, cannot be written
     * @throws XMLStreamException if there is a problem parsing using the StAX parser
     */
    @Test
    public void testSubdirInclude() throws ParserConfigurationException, SAXException, IOException,
        TransformerException, XMLStreamException {
        
        checkSameEvents(new File("src/test/resources/subdir-include.xml"));
    }
    
    /**
     * Tests parsing an XML file with multiple levels of includes.
     * 
     * @throws ParserConfigurationException if there is a problem using the DOM parser
     * @throws SAXException if there is a problem parsing using the DOM parser
     * @throws IOException if the XML file cannot be found
     * @throws TransformerException if the XML file, after includes, cannot be written
     * @throws XMLStreamException if there is a problem parsing using the StAX parser
     */
    @Test
    public void testNestedInclude() throws ParserConfigurationException, SAXException, IOException,
        TransformerException, XMLStreamException {
        
        checkSameEvents(new File("src/test/resources/nested-include.xml"));
    }
    
    /**
     * Tests that closing an include-aware reader closes the underlying
     * event readers for all included files.
     * 
     * @throws ParserConfigurationException if there is a problem using the DOM parser
     * @throws SAXException if there is a problem parsing using the DOM parser
     * @throws IOException if the XML file cannot be found
     * @throws TransformerException if the XML file, after includes, cannot be written
     * @throws XMLStreamException if there is a problem parsing using the StAX parser
     */
    @Test
    public void testCloseReader() throws ParserConfigurationException, SAXException, IOException,
        TransformerException, XMLStreamException {
        
        IncludeAwareEventReader reader = new IncludeAwareEventReader(new File("src/test/resources/direct-include.xml").toURI(), xmlInputFactory);
        reader.nextTag(); // Start tag in main file.
        reader.nextTag(); // Start tag in included file.
        reader.close();
        assertTrue(reader.isClosed());
    }
    
    /**
     * Tests that attempting to parse a nonexistent file
     * generates a parse exception.
     * 
     * @throws XMLStreamException if there is an error parsing the file 
     */
    @Test(expected = XMLStreamException.class)
    public void testParseNonexistentFile() throws XMLStreamException {
        readEventsFromFile(new IncludeAwareEventReader(new File("src/test/resources/nonexistent.xml").toURI(), xmlInputFactory));
    }
    
    /**
     * Tests that an &lt;xi:include&gt; for a nonexistent file
     * generates a parse exception.
     * 
     * @throws XMLStreamException if there is an error parsing the file 
     */
    @Test(expected = XMLStreamException.class)
    public void testIncludeNonexistentFile() throws XMLStreamException {
        readEventsFromFile(new IncludeAwareEventReader(new File("src/test/resources/include-nonexistent-file.xml").toURI(), xmlInputFactory));
    }
    
    /**
     * Tests that an &lt;xi:include&gt; without an href attribute
     * generates a parse exception.
     * 
     * @throws XMLStreamException if there is an error parsing the file 
     */
    @Test(expected = XMLStreamException.class)
    public void testIncludeWithoutHref() throws XMLStreamException {
        readEventsFromFile(new IncludeAwareEventReader(new File("src/test/resources/no-href-include.xml").toURI(), xmlInputFactory));
    }
    
    /**
     * Tests that an &lt;xi:include&gt; with a parsing type other than
     * XML generates a parse exception.
     * 
     * @throws XMLStreamException if there is an error parsing the file 
     */
    @Test(expected = XMLStreamException.class)
    public void testIncludeWithNonXMLParse() throws XMLStreamException {
        readEventsFromFile(new IncludeAwareEventReader(new File("src/test/resources/non-xml-include.xml").toURI(), xmlInputFactory));
    }
    
    /**
     * Tests that a file generates the same events when parsed with a StAX
     * parser both when preprocessing the &lt;xi:include&gt; instructions
     * using a DOM transformer and when parsed directly using an include-aware
     * event reader.
     * 
     * @param f the file to test
     * @throws ParserConfigurationException if there is a problem using the DOM parser
     * @throws SAXException if there is a problem parsing using the DOM parser
     * @throws IOException if the XML file cannot be found
     * @throws TransformerException if the XML file, after includes, cannot be written
     * @throws XMLStreamException if there is a problem parsing using the StAX parser
     */
    private void checkSameEvents(File f) throws ParserConfigurationException, SAXException, IOException,
        TransformerException, XMLStreamException {
        
        // Read the file using the DOM parser, processing any <code>xi:include</code>
        // elements.
        File tempFile = File.createTempFile(this.getClass().getSimpleName(), "xml");
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        assertTrue(builder.isXIncludeAware());
        Document doc = builder.parse(f);
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new FileOutputStream(tempFile));
        transformer.transform(source, result);
        
        // Now read both files and check that the event streams are the same, except for
        // ignorable whitespace.
        List<XMLEvent> stream1 = readEventsFromFile(new IncludeAwareEventReader(f.toURI(), xmlInputFactory));
        List<XMLEvent> stream2 = readEventsFromFile(xmlInputFactory.createXMLEventReader(new FileInputStream(tempFile)));
        
        checkSameEvents(stream2, stream1);
    }
    
    /**
     * Checks that two lists of parsing events are the same, by checking the event types.
     * 
     * @param stream1 the first event list
     * @param stream2 the second event list
     */
    private void checkSameEvents(List<XMLEvent> stream1, List<XMLEvent> stream2) {
        assertEquals(stream1.size(), stream2.size());

        Iterator<XMLEvent> it1 = stream1.iterator();
        Iterator<XMLEvent> it2 = stream2.iterator();
        while (it1.hasNext() && it2.hasNext()) {
            assertEquals(it1.next().getEventType(), it2.next().getEventType());
        }
    }

    /**
     * Gets the list of parsing events that occur when parsing an XML file
     * using a StAX reader. Ignores processing instructions and character
     * events that include only whitespace.
     * 
     * @param reader the event reader
     * @return a list of events
     * @throws XMLStreamException if there is an error parsing the XML file
     */
    private List<XMLEvent> readEventsFromFile(XMLEventReader reader) throws XMLStreamException {
        List<XMLEvent> events = new ArrayList<>();

        while (reader.peek() != null) {
            XMLEvent event = reader.nextEvent();
            if (event == null) {
                break;
            } else if (event.isProcessingInstruction()) {
                // Ignore
            } else if (event.isCharacters() && event.asCharacters().isWhiteSpace()) {
                // Ignore
            } else {
                events.add(reader.nextEvent());
                // Note that we don't read the element text. We are just looking
                // at the start/end element and document events.
            }
        }
        
        return events;
    }
    
}
