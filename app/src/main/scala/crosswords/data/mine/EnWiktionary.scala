
package crosswords.data.mine

import java.io._
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Tools used to automatically data mine from <a href="https://en.wiktionary.org/wiki/Wiktionary:Main_Page">Wiktionary</a>.
 * We used the latest archive that was currently available: <a href="https://dumps.wikimedia.org/enwiktionary/20150224/">dump from 2015-02-24</a>
 *
 *
 * @author Laurent Valette
 */
object EnWiktionary {
  private val _title = ".*<title>(.+)</title>.*"
  private val _ns = ".*<ns>0</ns>.*"
  private val _beginText = ".*<text.+>(.*)"
  private val _endText = "(.*)</text>.*"

  private val encoder = Base64.getUrlEncoder
  private val _folder = "../data/wiki/wikiMarkup/"

  /**
   * Parse crosswords from xml dump.
   * Write each article in a file named with its title converted in url safe Base64.
   * Use UTF-8 for both the input and output.
   * @param source source to parse
   * @see java.util.Base64
   */
  def parse(source: BufferedReader): Unit = {
    var insideArticle = false
    var insideText = false
    var title = ""
    var out: Writer = null

    for (line <- Stream.continually(source.readLine()).takeWhile(_ != null)) {
      if (line.matches(_title)) {
        // Get the title of the page
        title = _title.r.findFirstMatchIn(line).get.group(1)
      } else if (line.matches(_ns)) {
        // Check if this namespace is the one of an article
        insideArticle = true
      } else if (insideArticle && line.matches(_beginText)) {
        val encodedTitle = new String(encoder.encode(title.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)
        val fileOut = new FileOutputStream(new File(_folder + encodedTitle + ".txt"))
        out = new BufferedWriter(new OutputStreamWriter(fileOut, StandardCharsets.UTF_8))
        _beginText.r.findFirstMatchIn(line).map(_.group(1)) match {
          case Some(afterStart) =>
            // If there is some text after the <text> tag, we need to process it
            if (afterStart.matches(_endText)) {
              _endText.r.findFirstMatchIn(afterStart).map(_.group(1)) match {
                case Some(t) =>
                  out.write(t + "\n")
                case None => // nothing to do
              }
            } else {
              out.write(afterStart + "\n")
            }
          case None => // nothing to do
        }
        insideText = true
      } else if (line.matches(_endText)) {
        if (insideArticle) {
          _endText.r.findFirstMatchIn(line).map(_.group(1)) match {
            case Some(t) => out.write(t + "\n")
            case None => // nothing to do
          }
          out.close()
          out = null
        }
        insideArticle = false
        insideText = false
      } else if (insideArticle && insideText) {
        out.write(line + "\n")
      }
    }
  }


  def main(args: Array[String]): Unit = {
    val fileIn = new FileInputStream("../data/wiki/enwiki.xml")
    val reader = new InputStreamReader(fileIn, StandardCharsets.UTF_8)
    val src = new BufferedReader(reader)
    parse(src)
    src.close()
    reader.close()
    fileIn.close()
  }
}
