name := "boris"
organization := "co.horn"
version := "0.0.10"
scalaVersion := "2.12.3"
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
  val akkaHttpV    = "10.0.9"
  val akkaV        = "2.5.3" // Needed to force Akka HTTP 10.0.6 to use Current Akka version
  val scalaTestV   = "3.0.1"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor" % akkaV, // Remove this when Akka HTTP defaults to Akka 2.5.x
    "com.typesafe.akka" %% "akka-stream" % akkaV, // Remove this when Akka HTTP defaults to Akka 2.5.x
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}
