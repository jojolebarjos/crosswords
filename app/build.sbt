name := "app"

version := "1.0"

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.3.8"

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.8.0"

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "1.2.1",
  "org.apache.spark" %% "spark-mllib" % "1.2.1"
)

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"

libraryDependencies += "org.wikimodel" % "org.wikimodel.wem" % "2.0.7"

libraryDependencies += "org.codehaus.woodstox" % "woodstox-core-asl" % "4.4.1"
