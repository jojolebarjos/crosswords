
import java.net.URL
import edu.mit.jwi.item.POS
import edu.mit.jwi.morph.WordnetStemmer
import edu.mit.jwi.Dictionary
import java.io.File
import scala.collection.JavaConverters._

object Stem {
  
  def init = {
    // construct the URL to the Wordnet dictionary directory
    val wnhome = System.getenv("WNHOME")
    val path = wnhome + File.separator + "2.1" + File.separator + "dict"
    val url = new URL("file", null, path)

    // construct the dictionary object and open it
    val dict = new Dictionary(url)
    dict.open()
    dict
  }
  
  val dict = init
  val wordnetStemmer = new WordnetStemmer(dict)
   
  def getStem(word: String, pos: POS) = {
    wordnetStemmer.findStems(word, pos).asScala.toList
  }

  def main(args: Array[String]) {
    println(getStem("took", POS.VERB))
    println(getStem("taken", POS.VERB))
    println(getStem("connection", POS.NOUN))
    println(POS.values().toList)
    for (pos <- POS.values()) {
      println(getStem("the", pos))
    }
  }
}