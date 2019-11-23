# stax-xinclude
A StAX event reader for Java that supports Include

## Usage

To create an `XMLEventReader` for parsing documents that use Xinclude:

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = new XMLEventReader(documentURI, factory);
    ... use the event reader normally as if obtained from the factory directly ...
    XMLEvent event = eventReader.nextEvent();
    ...

## License

This project uses the Unlicense. See the `LICENSE` file for more details.
Essentially you can use this as you see fit. You do not need to credit me,
and you do not need to retain the license. That is, you can adopt this
code as your own under any license you see fit.
