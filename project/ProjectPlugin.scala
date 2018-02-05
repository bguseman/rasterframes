import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport.{ShadeRule, _}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import com.servicerocket.sbt.release.git.flow.Steps._
import xerial.sbt.Sonatype.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.sbtghpages.GhpagesPlugin
import com.typesafe.sbt.site.SitePlugin.autoImport._
import com.typesafe.sbt.site.paradox.ParadoxSitePlugin.autoImport._
import tut.TutPlugin.autoImport._
import GhpagesPlugin.autoImport._
import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport._

/**
 * @author sfitch
 * @since 8/20/17
 */
object ProjectPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  val versions = Map(
    "geotrellis" -> "1.2.0",
    "spark" -> "2.2.1"
  )

  import autoImport._

  override def projectSettings = Seq(
    organization := "io.astraea",
    organizationName := "Astraea, Inc.",
    startYear := Some(2017),
    homepage := Some(url("http://rasterframes.io")),
    scmInfo := Some(ScmInfo(url("https://github.com/s22s/raster-frames"), "git@github.com:s22s/raster-frames.git")),
    description := "RasterFrames brings the power of Spark DataFrames to geospatial raster data, empowered by the map algebra and tile layer operations of GeoTrellis",
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    scalaVersion := "2.11.12",
    scalacOptions ++= Seq("-target:jvm-1.8", "-feature", "-deprecation"),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    cancelable in Global := true,
    resolvers ++= Seq(
      "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
      "boundless-releases" at "https://repo.boundlessgeo.com/main/",
      Resolver.bintrayRepo("s22s", "maven"),
      "osgeo-releases" at "http://download.osgeo.org/webdav/geotools/"
    ),
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.2",
      "org.locationtech.geomesa" %% "geomesa-z3" % "1.3.5",
      "org.locationtech.geomesa" %% "geomesa-spark-jts" % "1.4.0-astraea.2",
      spark("core") % Provided,
      spark("mllib") % Provided,
      spark("sql") % Provided,
      geotrellis("spark"),
      geotrellis("raster"),
      geotrellis("spark-testkit") % Test excludeAll (
        ExclusionRule(organization = "org.scalastic"),
        ExclusionRule(organization = "org.scalatest")
      ),
      scalaTest
    ),
    publishTo := sonatypePublishTo.value,
    publishMavenStyle := true,
    publishArtifact in (Compile, packageDoc) := true,
    publishArtifact in Test := false,
    fork in Test := true,
    javaOptions in Test := Seq("-Xmx2G"),
    parallelExecution in Test := false,
    developers := List(
      Developer(
        id = "metasim",
        name = "Simeon H.K. Fitch",
        email = "fitch@astraea.io",
        url = url("http://www.astraea.io")
      ),
      Developer(
        id = "mteldridge",
        name = "Matt Eldridge",
        email = "meldridge@astraea.io",
        url = url("http://www.astraea.io")
      )
    )
  )

  object autoImport {

    def geotrellis(module: String) =
      "org.locationtech.geotrellis" %% s"geotrellis-$module" % versions("geotrellis")
    def spark(module: String) =
      "org.apache.spark" %% s"spark-$module" % versions("spark")

    val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3" % Test

    def releaseSettings: Seq[Def.Setting[_]] = {
      val buildSite: (State) ⇒ State = releaseStepTask(makeSite)
      val publishSite: (State) ⇒ State = releaseStepTask(ghpagesPushSite)
      Seq(
        releaseIgnoreUntrackedFiles := true,
        releaseTagName := s"${version.value}",
        releaseProcess := Seq[ReleaseStep](
          checkSnapshotDependencies,
          checkGitFlowExists,
          inquireVersions,
          runTest,
          gitFlowReleaseStart,
          setReleaseVersion,
          buildSite,
          publishSite,
          commitReleaseVersion,
          tagRelease,
          releaseStepCommand("publishSigned"),
          gitFlowReleaseFinish,
          setNextVersion,
          commitNextVersion,
          releaseStepCommand("sonatypeReleaseAll")
        ),
        commands += Command.command("bumpVersion"){ st ⇒
          val extracted = Project.extract(st)
          val ver = extracted.get(version)
          val nextFun = extracted.runTask(releaseNextVersion, st)._2

          val nextVersion = nextFun(ver)

          val file = extracted.get(releaseVersionFile)
          IO.writeLines(file, Seq(s"""version in ThisBuild := "$nextVersion""""))
          extracted.append(Seq(version := nextVersion), st)
        }
      )
    }

    def docSettings: Seq[Def.Setting[_]] = Seq(
      git.remoteRepo := "git@github.com:s22s/raster-frames.git",
      apiURL := Some(url("http://rasterframes.io/latest/api")),
      autoAPIMappings := false,
      paradoxProperties in Paradox ++= Map(
        "github.base_url" -> "https://github.com/s22s/raster-frames",
        "scaladoc.org.apache.spark.sql.gt" -> "http://rasterframes.io/latest",
        "scaladoc.geotrellis.base_url" -> "https://geotrellis.github.io/scaladocs/latest"
      ),
      sourceDirectory in Paradox := tutTargetDirectory.value,
      sourceDirectory in Paradox in paradoxTheme := sourceDirectory.value / "main" / "paradox" / "_template",
      makeSite := makeSite.dependsOn(tutQuick).value,
      ghpagesNoJekyll := true,
      scalacOptions in (Compile, doc) ++= Seq(
        "-no-link-warnings"
      ),
      libraryDependencies ++= Seq(
        spark("mllib") % Tut,
        spark("sql") % Tut,
        geotrellis("spark") % Tut,
        geotrellis("raster") % Tut
      ),
      fork in (Tut, run) := true,
      javaOptions in (Tut, run) := Seq("-Xmx8G", "-Dspark.ui.enabled=false"),
      unmanagedClasspath in Tut ++= (fullClasspath in (LocalProject("datasource"), Compile)).value
    )

    def buildInfoSettings: Seq[Def.Setting[_]] = Seq(
      buildInfoKeys ++= Seq[BuildInfoKey](
        name, version, scalaVersion, sbtVersion
      ) ++ versions.toSeq.map(p => (p._1 + "Version", p._2): BuildInfoKey),
      buildInfoPackage := "astraea.spark.rasterframes",
      buildInfoObject := "RFBuildInfo",
      buildInfoOptions := Seq(
        BuildInfoOption.ToMap,
        BuildInfoOption.BuildTime
      )
    )

    def assemblySettings: Seq[Def.Setting[_]] = Seq(
      test in assembly := {},
      assemblyMergeStrategy in assembly := {
        case "logback.xml" ⇒ MergeStrategy.singleOrError
        case "git.properties" ⇒ MergeStrategy.discard
        case x if Assembly.isConfigFile(x) ⇒ MergeStrategy.concat
        case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) ⇒
          MergeStrategy.rename
        case PathList("META-INF", xs @ _*) ⇒
          (xs map { _.toLowerCase }) match {
            case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) ⇒
              MergeStrategy.discard
            case ps @ (x :: _) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") ⇒
              MergeStrategy.discard
            case "plexus" :: _ ⇒
              MergeStrategy.discard
            case "services" :: _ ⇒
              MergeStrategy.filterDistinctLines
            case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) ⇒
              MergeStrategy.filterDistinctLines
            case ("maven" :: rest ) if rest.lastOption.exists(_.startsWith("pom")) ⇒
              MergeStrategy.discard
            case _ ⇒ MergeStrategy.deduplicate
          }

        case _ ⇒ MergeStrategy.deduplicate
      }
    )
  }
}
