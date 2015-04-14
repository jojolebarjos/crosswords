
package crosswords.mine.wiki

import java.io.InputStream
import javax.xml.stream.{XMLInputFactory, XMLStreamReader}
import javax.xml.stream.XMLStreamConstants._

/**
 * Iterate over pages of specified Wikipedia dump XML file.
 *
 * @author Johan Berdat
 */
class Pages(val reader: XMLStreamReader) extends Iterator[(String, String)] {

  private var title, text = ""
  private var found = false
  advance()

  def this(input: InputStream) = this {
    // Xerxes implementation seems to have issues with such large XML
    // XMLInputFactory.newInstance().createXMLStreamReader(input)
    val factory = new com.ctc.wstx.stax.WstxInputFactory()
    factory.configureForSpeed()
    factory.createXMLStreamReader(input)
  }

  private def advance() {
    found = false
    var ns = false
    while (!found && reader.hasNext)
      if (reader.next() == START_ELEMENT)
        reader.getLocalName match {
          case "title" =>
            title = reader.getElementText()
            ns = false
          case "ns" =>
            ns = reader.getElementText() == "0"
          case "text" if ns =>
            text = reader.getElementText()
            found = true
          case _ =>
        }
  }

  override def hasNext = found

  override def next() = {
    val result = (title, text)
    advance()
    result
  }

}
