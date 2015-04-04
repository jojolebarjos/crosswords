import edu.mit.jwi.item.IWord
import edu.mit.jwi.item.IWordID
import java.net.URL
import edu.mit.jwi.item.POS
import edu.mit.jwi.item.IIndexWord
import edu.mit.jwi.morph.WordnetStemmer
import edu.mit.jwi.Dictionary
import java.io.File

object Stem {
  
  def init = {
    // construct the URL to the Wordnet dictionary directory
    val wnhome = System.getenv("WNHOME");
    val path = wnhome + File.separator + "2.1" + File.separator + "dict";
    val url = new URL("file", null, path);

    // construct the dictionary object and open it
    val dict = new Dictionary(url);
    dict.open();
    dict
  }
  
  val dict = init
   
  def getStem(word: String) = {
    val wordnetStemmer = new WordnetStemmer(dict);
    wordnetStemmer.findStems(word, POS.VERB)
  }

  def main(args: Array[String]) {
    dict
    println(getStem("took"))
  }
}