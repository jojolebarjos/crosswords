package crosswords.util

import java.net.URL
import edu.mit.jwi.item.POS
import edu.mit.jwi.morph.WordnetStemmer
import edu.mit.jwi.Dictionary
import scala.collection.JavaConverters._
import java.text.Normalizer

/**
 * Created by Vincent on 14.04.2015.
 */
object SparkStemmer {

  var stemToId = collection.mutable.Map[String, Int]()
  var dictionary = collection.mutable.Map[String, Int]()
  var id = 0
  val dict = init
  val wordnetStemmer = new WordnetStemmer(dict)
  val regex_ASCII = """[^\p{ASCII}]"""
  val regex_UNI = """\p{M}"""

  def init = {
    // construct the URL to the Wordnet dictionary directory
    val wnhome = System.getenv("WNHOME")
    val path = """C:\Users\Vincent\EPFL\Big Data\Wordnet test\WordNet-3.0\WordNet-3.0\dict"""
    val url = new URL("file", null, path)

    // construct the dictionary object and open it
    val dict = new Dictionary(url)
    dict.open()
    dict
  }


  def getStem(word: String, pos: POS) = {
    wordnetStemmer.findStems(word, pos).asScala.toList
  }


  /// input : <word> <weight> <clue>   ex :                HANDED 10 HAVING A PECULIAR OR CHARACTERISTIC HAND
  /// output : <id> <word> <weight> <reduced clue> ex :    123 HANDED 10 HAVING PECULIAR CHARACTERISTIC HAND
  def line_stemer(sentence : String) : Seq[String] = {
      sentence
      .toUpperCase()
      .split(" ")
      .map(
       word => Normalizer.normalize(word, Normalizer.Form.NFD)
         .replaceAll(regex_ASCII, ""))
      .map(word => word_stemmer(word))

  }

  def word_stemmer(word : String) : String = {
    "vakavkaf"
  }

  def getDictionary() = dictionary

}
