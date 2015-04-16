
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
  val porterStemmer = new PorterStemmer()

  //TODO: Need to be completed
  private val bad_cases = List(
    "AM", "IS", "AR",
    "A"
  )

  // TODO more
  private val escapes = Map(
    """’""" -> """'""",
    "Æ" -> "AE",
    "Œ" -> "OE",
    """'LL""" -> """'WILL""",
    """CAN'T""" -> """CAN NOT""",
    """N'T""" -> """ NOT""",
    """'D""" -> """ HAD"""
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
    val wordNetHome = System.getenv("WNHOME")
    val path = wordNetHome + File.separator + "WordNet-3.0" + File.separator + "dict"
    val url = new URL("file",null,path)

    val wordNetDictionary = new Dictionary(url)
    wordNetDictionary .open()
    wordNetDictionary
  }


  private def getStem(word : String) ={
    if (word.matches("""\s*""")) Nil
    else wordnetStemmer.findStems(word, null).asScala.toList
  }

  def main(args: Array[String]) {
    for (line <- Source.stdin.getLines()) {
      println(clean(line))
    }
  }

  ///TODO : check for ligature. Ask if they have to be split. ex : Æ is considered a letter in its own right
  def normalize(word : String) : String ={
    Normalizer.normalize(removeEscapes(word), Normalizer.Form.NFKD)
          .replaceAll("""[^\p{ASCII}]""", "").replaceAll("""[^_\w]""", " ")
  }

  def reduce(word : String) : String = {
      val stems = getStem(word)
        .map(w =>
        //porterStemmer.getPorterStem(w)
          //.toUpperCase())
        w.toUpperCase())
        .toSet
        .filter(w => !bad_cases.contains(w))

    if (!stems.isEmpty) stems.minBy(_.length) else ""
  }



  /**
   * Clean and simplify text.
   */
  def clean(sentence: String): Seq[String] = {
    normalize(sentence).split(" ")
    .map(reduce(_)).filter(_ != "") //.map(normalize(_))
  }



  //TODO clean this shit up and put all letters
  class PorterStemmer{
    def getPorterStem(word : String) : String ={
      add(word)
      if (b.length > 2){
        step1()
        step2()
        step3()
        step4()
        step5a()
        step5b()
      }
      b
    }
    // word to be stemmed.
    var b = ""

    // Just recode the existing stuff, then go through and refactor with some intelligence.
    private def cons(i: Int): Boolean =
    {
      var ch = b(i)

      // magic!
      var vowels = "aeiou"

      // multi return. yuck
      if (vowels.contains(ch))
        return false

      if (ch == 'y') {
        if (i == 0) {
          return true
        } else {
          // loop it!
          return !cons(i - 1)
        }
      }

      return true

    }

    // Add via letter or entire word
    private def add(ch: Char) =
    {
      b += ch
    }

    private def add(word: String) =
    {
      b = word
    }

    private def calcM(s: String): Int =
    {
      var l = s.length
      var count = 0
      var currentConst = false

      for (c <- 0 to l - 1) {
        if (cons(c)) {
          if (!currentConst && c != 0) {
            count += 1
          }
          currentConst = true
        } else {
          currentConst = false
        }
      }

      return count
    }

    // removing the suffix 's', does a vowel exist?'
    private def vowelInStem(s: String): Boolean =
    {

      for (i <- 0 to b.length - 1 - s.length) {
        if (!cons(i)) {
          return true
        }
      }

      return false;

    }

    /* doublec(j) is true <=> j,(j-1) contain a double consonant. */
    private def doublec(): Boolean =
    {
      var l = b.length - 1

      if (l < 1)
        return false

      if (b(l) != b(l - 1))
        return false

      return cons(l)

    }



    private def cvc(s: String): Boolean =
    {
      var i = b.length - 1 - s.length
      if (i < 2 || !cons(i) || cons(i - 1) || !cons(i - 2))
        return false;

      var ch = b(i)

      var vals = "wxy"

      if (vals.contains(ch))
        return false

      return true;
    }

    // returns true if it did the change.
    def replacer(orig: String, replace: String, checker: Int => Boolean): Boolean =
    {

      var l = b.length
      var origLength = orig.length
      var res = false

      if (b.endsWith(orig)) {
        var n = b.substring(0, l - origLength)

        var m = calcM(n)
        if (checker(m)) {
          b = n + replace
        }

        res = true

      }

      return res
    }

    // process the list of tuples to find which prefix matches the case.
    // checker is the conditional checker for m.
    def processSubList(l: List[(String, String)], checker: Int => Boolean): Boolean =
    {
      var iter = l.toIterator
      var done = false

      while (!done && iter.hasNext) {
        var v = iter.next
        done = replacer(v._1, v._2, checker)

      }

      return done
    }

    def step1() {

      var l = b.length

      var m = calcM(b)

      // step 1a
      var vals = List(("sses", "ss"), ("ies", "i"), ("ss", "ss"), ("s", ""))
      processSubList(vals, _ >= 0)

      // step 1b
      if (!(replacer("eed", "ee", _ > 0))) {

        if ((vowelInStem("ed") && replacer("ed", "", _ >= 0)) || (vowelInStem("ing") && replacer("ing", "", _ >= 0))) {

          vals = List(("at", "ate"), ("bl", "ble"), ("iz", "ize"))

          if (!processSubList(vals, _ >= 0)) {
            // if this isn't done, then it gets more confusing.

            m = calcM(b)
            var last = b(b.length - 1)
            if (doublec() && !"lsz".contains(last)) {
              b = b.substring(0, b.length - 1)
            } else if (m == 1 && cvc("")) {
              b = b + "e"
            }
          }
        }
      }

      // step 1c

      (vowelInStem("y") && replacer("y", "i", _ >= 0))

    }

    def step2() =
    {

      var vals = List(("ational", "ate"), ("tional", "tion"), ("enci", "ence"), ("anci", "ance"), ("izer", "ize"), ("bli", "ble"), ("alli", "al"),
        ("entli", "ent"), ("eli", "e"), ("ousli", "ous"), ("ization", "ize"), ("ation", "ate"), ("ator", "ate"), ("alism", "al"),
        ("iveness", "ive"), ("fulness", "ful"), ("ousness", "ous"), ("aliti", "al"), ("iviti", "ive"), ("biliti", "ble"), ("logi", "log"))

      processSubList(vals, _ > 0)

    }

    private def step3() =
    {

      var vals = List(("icate", "ic"), ("ative", ""), ("alize", "al"), ("iciti", "ic"), ("ical", "ic"), ("ful", ""), ("ness", ""))

      processSubList(vals, _ > 0)

    }

    private def step4() =
    {

      // first part.
      var vals = List(("al", ""), ("ance", ""), ("ence", ""), ("er", ""), ("ic", ""), ("able", ""), ("ible", ""), ("ant", ""), ("ement", ""),
        ("ment", ""), ("ent", ""))

      var res = processSubList(vals, _ > 1)

      // special part.
      if (!res) {
        if (b.length > 4) {
          if (b(b.length - 4) == 's' || b(b.length - 4) == 't') {
            res = replacer("ion", "", _ > 1)

          }
        }

      }

      // third part.
      if (!res) {
        var vals = List(("ou", ""), ("ism", ""), ("ate", ""), ("iti", ""), ("ous", ""), ("ive", ""), ("ize", ""))
        res = processSubList(vals, _ > 1)

      }

    }

    private def step5a() =
    {

      var res = false

      res = replacer("e", "", _ > 1)

      if (!cvc("e")) {
        res = replacer("e", "", _ == 1)
      }

    }

    private def step5b() =
    {

      var res = false
      var m = calcM(b)
      if (m > 1 && doublec() && b.endsWith("l")) {
        b = b.substring(0, b.length - 1)
      }
    }
  }




}


