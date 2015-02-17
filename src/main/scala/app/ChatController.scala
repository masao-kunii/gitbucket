package app

import jp.sf.amateras.scalatra.forms._

import service._
import IssuesService._
import util._
import util.Implicits._
import util.ControlUtil._
import org.scalatra.Ok
import model.Issue
import org.scalatra._
import org.scalatra.atmosphere._
import org.scalatra.servlet.AsyncSupport
import org.scalatra.json.{JValueResult, JacksonJsonSupport}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import JsonDSL._
import java.util.Date
import java.text.SimpleDateFormat
import org.apache.commons.io.FileUtils
import scala.concurrent._
import ExecutionContext.Implicits.global
import java.io.File
import collection.JavaConversions._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}

class ChatController extends ChatControllerBase
with RepositoryService with AccountService with LabelsService with MilestonesService with ActivityService
with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator

case class Message(user: String, text: String, kind: String, chatroom: String, timestamp: String)
//case class Messages(messages:List[Message])

trait ChatControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with LabelsService with MilestonesService with ActivityService
    with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  case class ChatForm(content: String)
  val chatForm = mapping(
    "content" -> trim(label("Comment", text(required)))
  )(ChatForm.apply)

  get("/:owner/:repository/chatroom")(readableUsersOnly { repository =>
    val dir = new File(_root_.util.Directory.GitBucketHome + "/chat")
    val log = new File(s"${dir.getAbsolutePath}/${repository.owner}_${repository.name}.log")
    if(!dir.exists){
      dir.mkdirs()
      FileUtils.touch(log)
    }

    implicit val jsonFormats: Formats = DefaultFormats
    val chatLog:Seq[String] = FileUtils.readLines(log, "UTF-8")
    val messageLogs =  for(e <- chatLog) yield parse(e).extract[Message]


//    val strMessageLogs = FileUtils.readFileToString(log, "UTF-8")
//    val messageLogs = if(strMessageLogs.isEmpty) None else Some(parse(log).extract[Messages])

      defining(repository.owner, repository.name){ case (owner, name) =>
      chat.html.chatroom(
        (getCollaborators(owner, name) ::: (if(getAccountByUserName(owner).get.isGroupAccount) Nil else List(owner))).sorted,
        getMilestones(owner, name),
        getLabels(owner, name),
        hasWritePermission(owner, name, context.loginAccount),
        repository,
        messageLogs)
//        if (messageLogs.isEmpty) Seq.empty[Message] else messageLogs.get.messages)
    }
  })

}
