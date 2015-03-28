val wiki_redirect = """\[\[([^\[\]\|]+)\]\]""".r
val wiki_piped_link = """\[\[([^\[\]\|]+:?[^\[\]\|]+)(\|(.+))\]\]""".r
val test_string = "hello [[get this stuff here]] this leads to [[lalala|a problem]]"

var result_1 = ""
var result_2 = ""
wiki_redirect.findAllIn(test_string).matchData.foreach(m => result_1 = test_string.replaceAllLiterally(m.toString, m.group(1)))
test_string + " result = 1 " + result_1


wiki_piped_link.findAllIn(test_string).matchData.foreach(m => result_2 = test_string.replaceAllLiterally(m.toString, m.group(3)))
test_string + "result 2 = " + result_2

val s = "  hello   bla  "
s.trim
