import scala.io.Source

object runit {
  def main(args: Array[String]) =
    {

//      val filename = args(0)
//      val source = Source.fromFile(filename)
      var stemmer = new Stemmer()
//
//      for (line <- source.getLines) {

//        var l = line.trim()
  
      var l = "connecting"
        stemmer.add(l)

        if (stemmer.b.length > 2) {
          stemmer.step1()
          stemmer.step2()
          stemmer.step3()
          stemmer.step4()
          stemmer.step5a()
          stemmer.step5b()
        }

        println(stemmer.b)
//      }
    }
}