name := "boris"
organization := "co.horn"
scalaVersion := "2.13.12"
crossScalaVersions := Seq("2.12.15", "2.13.12")

semanticdbEnabled in ThisBuild := true
semanticdbVersion in ThisBuild := scalafixSemanticdb.revision

fork in Test := true

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-Ywarn-unused",
  "-encoding",
  "UTF-8",
  "-target:jvm-1.8",
  "-feature",
  "-language:postfixOps"
)

publishTo := Some("Horn Packages" at "https://maven.pkg.github.com/8eo/boris")

libraryDependencies ++= {
  val akkaHttpV  = "10.2.9"
  val akkaV      = "2.6.19"
  val scalaTestV = "3.2.12"

  Seq(
    "com.typesafe.akka" %% "akka-http"            % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor"           % akkaV,
    "com.typesafe.akka" %% "akka-stream"          % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpV  % "test",
    "org.scalatest"     %% "scalatest"            % scalaTestV % "test"
  )
}
