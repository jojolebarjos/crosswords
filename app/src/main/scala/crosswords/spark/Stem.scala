package crosswords.spark

import java.net.URL
import java.text.Normalizer
import java.io.File

import edu.mit.jwi.Dictionary
import edu.mit.jwi.morph.WordnetStemmer

import scala.io.Source
import scala.collection.JavaConverters._

/**
 * Clean words and find roots.
 *
 * @author Johan Berdat
 * @author Grégory Maitre
 * @author Vincent Mettraux
 * @author Patrick Andrade
 */
object Stem {

  var stemToId = collection.mutable.Map[String,Int]()
  var dictionary = collection.mutable.Map[String, Int]()
  var id = 0
  val dict = initWordNetDictionary
  val wordnetStemmer = new WordnetStemmer(dict)

  //TODO: Complete it when bad cases are identified.
  private val bad_cases = List("AM", "IS", "AR", "A")

  // TODO: Complete it when corner cases are identified.
  private val escapes = Map(
    """’""" -> """'""",
    "Æ" -> "AE",
    "Œ" -> "OE",
    """'LL""" -> """'WILL""",
    """CAN'T""" -> """CAN NOT""",
    """N'T""" -> """ NOT""",
    """'D""" -> """ HAD""",
    """'S""" -> "" //joe's shop
  )

  /**
   * Convert to uppercase ASCII and remove symbols.
   */
  def toAscii(text: String): String = {
    var tmp = text.toUpperCase
    for ((a, b) <- escapes)
      tmp = tmp.replace(a, b)
    tmp.replaceAll("[_\\W]+", " ").trim
  }

  def removeEscapes(text: String): String = {
    var tmp = text.toUpperCase
    for ((a, b) <- escapes)
      tmp = tmp.replaceAll(a, b)
    tmp
  }

  private def initWordNetDictionary = {
    //val wordNetHome = System.getenv("WNHOME")
    val wordNetHome = "../data/wordnet"
    val path = wordNetHome + File.separator + "WordNet-3.0" + File.separator + "dict"
    val url = new URL("file",null,path)

    val wordNetDictionary = new Dictionary(url)
    wordNetDictionary .open()
    wordNetDictionary
  }

  private def getStem(word : String) ={
    if (word.matches("""\s*""")) Nil
    else try {wordnetStemmer.findStems(word, null).asScala.toList} catch {case e: IllegalArgumentException => Nil}
  }

  def main(args: Array[String]) {
    println(clean("Yes, we 5 across ____s"))
    for (line <- Source.stdin.getLines()) {
      println(clean(line))
    }
  }

  ///TODO : check for ligature. Ask if they have to be split. ex : Æ is considered a letter in its own right
  def normalize(word : String) : String ={
    Normalizer.normalize(removeEscapes(word), Normalizer.Form.NFKD)
          .replaceAll("""[^\p{ASCII}]""", "").replaceAll("""[^\w]""", " ").replaceAll("""_""", "")
  }

  def reduce(word : String) : String = {
      val stems = getStem(word)
        .map(w => w.toUpperCase())
        .toSet
        .filter(w => !bad_cases.contains(w))

    if (!stems.isEmpty) stems.minBy(_.length) else ""
  }

  /**
   * Clean and simplify text.
   */
  def clean(sentence: String): Seq[String] = {
    normalize(sentence).split(" ").map(reduce(_)).filter(_ != "")
  }
}