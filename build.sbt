name := "boris"
organization := "co.horn"
version := "0.0.16"
scalaVersion := "2.12.5"
//Do not remove it it's used in lambda package and it's still 2.11
//because of https://github.com/datastax/spark-cassandra-connector
+crossScalaVersions := Seq("2.11.8", "2.12.5")
fork in Test := true

scalacOptions ++= Seq("-unchecked",
                      "-deprecation",
                      "-Ywarn-dead-code",
                      "-encoding",
                      "UTF-8",
                      "-target:jvm-1.8",
                      "-feature",
                      "-language:postfixOps")

libraryDependencies ++= {
  val akkaHttpV = "10.1.0"
  val akkaV = "2.5.11"
  val scalaTestV = "3.0.5"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}
