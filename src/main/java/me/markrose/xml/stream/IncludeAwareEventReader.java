package me.markrose.xml.stream;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Implements an {@link XMLEventReader} that process Xinclude elements.
 * The include support is limited: it only supports XML parsing of
 * included documents, and only supports references in the <code>href</code>
 * attribute. The <code>fallback</code> sub-element is not supported.
 * In addition, the include locations are resolved using the {@link URI}
 * class, which may not correctly support all references.
 */
public class IncludeAwareEventReader implements XMLEventReader {

    private static final String XINCLUDE_NAMESPACE = "http://www.w3.org/2001/XInclude";
    private static final QName XINCLUDE_TAG = new QName(XINCLUDE_NAMESPACE, "include");
    private static final QName XINCLUDE_HREF_ATTR = new QName("href");
    private static final QName XINCLUDE_PARSE_ATTR = new QName("parse");

    private XMLInputFactory factory;
    private Deque<EventContext> contextStack = new ArrayDeque<>();
    
    public IncludeAwareEventReader(URI location, XMLInputFactory factory) throws XMLStreamException {
        this.factory = factory;
        try {
            pushContext(location);
        } catch (IOException e) {
            throw new XMLStreamException("Error reading XML stream", e);
        }
    }
    
    /**
     * Pushes a new event context onto the stack. Event processing will
     * continue from the included file.
     * 
     * @param location the new XML file location
     * @throws MalformedURLException if the location of the XML file is not a valid URL
     * @throws XMLStreamException if there is an error creating an event reader for the location
     * @throws IOException if there is an error reading from the XML file location
     */
    private void pushContext(URI location) throws MalformedURLException, XMLStreamException, IOException {
        XMLEventReader reader = factory.createXMLEventReader(location.toURL().openStream());
        contextStack.addLast(new EventContext(location, reader));
    }
    
    /**
     * Pops the current event context so that event processing will resume
     * in the file that requested an <code>xi:include</code>.
     */
    private void popContext() {
        if (contextStack.isEmpty()) {
            throw new IllegalStateException("Should not happen: too many calls to popLocation()");
        }
        contextStack.removeLast();
    }
    
    /**
     * Tests whether we are processing events from an included file.
     * 
     * @return true, if events are coming from an included file, false otherwise
     */
    private boolean isProcessingInclude() {
        return contextStack.size() > 1;
    }
    
    /**
     * Gets the current event reading context.
     * 
     * @return the current context
     */
    private EventContext currentContext() {
        return contextStack.getLast();
    }
    
    /**
     * Gets the current XML event reader.
     * 
     * @return the current XML event reader
     */
    private XMLEventReader currentReader() {
        return currentContext().getReader();
    }
    
    @Override
    public Object next() {
        try {
            return nextEvent();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Unexpected error reading XML events", e);
        }
    }

    @Override
    public XMLEvent nextEvent() throws XMLStreamException {
        XMLEvent event = peek();
        
        // We're guaranteed to have a start/end tag, a start/end document
        // at the outermost include level, a characters event, or a null
        // event at the outermost include level, so it is
        // safe to ask the current reader to get the event.
        return currentReader().nextEvent();
    }

    @Override
    public boolean hasNext() {
        try {
            return peek() != null;
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Error determining whether more events exist", e);
        }
    }

    @Override
    public String getElementText() throws XMLStreamException {
        return currentReader().getElementText();
    }

    @Override
    public XMLEvent nextTag() throws XMLStreamException {
        XMLEvent event = peek();
        
        // We're guaranteed to have a start/end tag, a start/end document
        // at the outermost include level, a characters event, or a null
        // event at the outermost include level, so it is
        // safe to ask the current reader to get the event.
        return currentReader().nextTag();
    }

    @Override
    public XMLEvent peek() throws XMLStreamException {
        // Peeks at the next event, but also processes xi:include
        // tags, pushing a new reading context if an include is found.
        
        for (;;) {
            XMLEvent event = currentReader().peek();
            
            if (isIncludeEvent(event)) {
                // The event is a new <code>xi:include</code.
                processInclude(event.asStartElement());
            } else if (!isProcessingInclude()) {
                // We don't filter events at the top level.
                return event;
            } else if (event == null) {
                // No more events from an included file.
                popContext();
            } else if (event.isStartDocument() || event.isEndDocument()) {
                // Skip start/end document events from an included file.
                currentReader().nextEvent();
            } else {
                // The event is not filtered, so return it unchanged. 
                return event;
            }
        }
    }
    
    /**
     * Tests whether an XML event is the start of an <code>xi:include</code>
     * element.
     * 
     * @param event the XML stream event
     * @return true, if the event is the start of an include element
     */
    private boolean isIncludeEvent(XMLEvent event) {
        if (event==null || !event.isStartElement()) {
            return false;
        }
        
        return event.asStartElement().getName().equals(XINCLUDE_TAG);
    }
    
    /**
     * Processes an <code>xi:include</code> element. Determines the
     * location of the included XML file, relative to the current
     * location, and starts a new event context from that new location.
     * 
     * @param element the include element
     * @throws XMLStreamException if there is an error parsing the XML file
     */
    private void processInclude(StartElement element) throws XMLStreamException {
        // Move into the start element and skip to the end of
        // the <code>xi:include</code> element.
        currentReader().nextEvent();
        currentReader().getElementText();
        
        Attribute hrefAttr = element.getAttributeByName(XINCLUDE_HREF_ATTR);
        if (hrefAttr == null) {
            throw new XMLStreamException("XML include requires href attribute: " + element);
        }
        
        // Check that either "parse" is missing or is XML.
        Attribute parseAttr = element.getAttributeByName(XINCLUDE_PARSE_ATTR);
        if (parseAttr!=null && !parseAttr.getValue().equals("xml")) {
            throw new XMLStreamException("Only XML parsing is supported in included files: " + element);
        }
        
        try {
            pushContext(currentContext().getLocation().resolve(hrefAttr.getValue()));
        } catch (IOException e) {
            throw new XMLStreamException("Cannot read from included XML file: " + element);
        }
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        // Always return properties of the outermost reader.
        return contextStack.getFirst().getReader().getProperty(name);
    }

    @Override
    public void close() throws XMLStreamException {
        // Close all open readers.
        while (!contextStack.isEmpty()) {
            EventContext loc = contextStack.removeLast();
            loc.getReader().close();
        }
    }

    /**
     * Tests that all event readers have been popped from the stack.
     * Default scope for unit testing.
     * 
     * @return true, if the context stack is empty
     */
    boolean isClosed() {
        return contextStack.isEmpty();
    }
    
    /**
     * Implements a container of an XML file location and an event
     * reader for that location.
     */
    private static class EventContext {
        
        private URI location;
        private XMLEventReader reader;
        
        public EventContext(URI location, XMLEventReader reader) {
            this.location = location;
            this.reader = reader;
        }

        public URI getLocation() {
            return location;
        }

        public XMLEventReader getReader() {
            return reader;
        }
        
    }

}
