
package crosswords.data.mine

import java.io._
import java.nio.charset.StandardCharsets
import java.util.Base64

import play.api.libs.json.JsObject

/**
 * Tools used to automatically data mine from <a href="https://en.wiktionary.org/wiki/Wiktionary:Main_Page">Wiktionary</a>.
 * We used the latest archive that was currently available: http://dumps.wikimedia.org/enwiktionary/20150224/enwiktionary-20150224-pages-articles-multistream.xml.bz2
 *
 * @author Laurent Valette
 */
object EnWiktionary {

  // http://en.wiktionary.org/wiki/Wiktionary:ELE
  // http://en.wiktionary.org/wiki/Wiktionary:Semantic_relations
  /*
  ==English==
  ===Alternative forms===
  ===Etymology===
  ===Pronunciation===
  * Phonetic transcriptions
  * Audio files in any relevant dialects
  * Rhymes
  * Homophones
  * Hyphenation
  ===Noun===
  Declension
  # Meaning 1
  #* Quotations
  # Meaning 2
  #* Quotations
       etc.
  ====Usage notes====
  ====Synonyms====
  ====Antonyms====
  ====Derived terms====
  ====Related terms====
  ====Translations====
  ====References====
  ====External links====
  ===Verb===
  Conjugation
  # Meaning 1
  #* Quotations
       etc.
  ====Usage notes====
  ====Synonyms====
  ====Antonyms====
  ====Derived terms====
  ====Related terms====
  ====Translations====
  ====Descendants====
  ====References====
  ====External links====
  ===Anagrams===
  ---- (Dividing line between languages)
  ==Finnish==
  ===Etymology===
  ===Pronunciation===
  ===Noun===
  '''Inflections'''
  # Meaning 1 in English
  #* Quotation in Finnish
  #** Quotation translated into English
  # Meaning 2 in English
  #* Quotation in Finnish
  #** Quotation translated into English
  ====Synonyms====
  ====Derived terms====
  ====Related terms====
   */

  private val _title = ".*<title>(.+)</title>.*"
  private val _ns = ".*<ns>0</ns>.*"
  private val _beginText = ".*<text.+>(.*)"
  private val _endText = "(.*)</text>.*"

  private val encoder = Base64.getUrlEncoder

  private val _noun = """===Noun===""".r
  private val _verb = """===Verb===""".r
  private val _adj = """===Adjective===""".r

  private val _synonyms = """====Synonyms====""".r
  private val _antonyms = """====Antonyms====""".r
  private val _derived = """====Derived terms====""".r
  private val _related = """====Related terms====""".r

  private val _folder = "C:/Users/Vincent/EPFL/Big Data/xml_out/"


  /**
   * Parse crosswords from HTML text.
   * @param source source to parse
   * @return a clearer text file with the relevent information to be subsequently parsed into a json file
   */
  def parse(source: BufferedReader): JsObject = {
    var insideArticle = false
    var insideText = false
    var title = ""
    var out: OutputStreamWriter = null

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
        out = new OutputStreamWriter(fileOut, StandardCharsets.UTF_8)
        _beginText.r.findFirstMatchIn(line).map(_.group(1)) match {
          case Some(afterStart) =>
            // If there is some text after <text> tag, we need to process it
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
    null
  }


  def main(args: Array[String]): Unit = {
    val fileIn = new FileInputStream("C:/Users/Vincent/EPFL/Big Data/enwiki.xml")
    val reader = new InputStreamReader(fileIn, StandardCharsets.UTF_8)
    val src = new BufferedReader(reader)
    parse(src)
    src.close()
    reader.close()
    fileIn.close()
  }
}
