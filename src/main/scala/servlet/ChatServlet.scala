package servlet

import java.util.concurrent.ConcurrentHashMap

import _root_.service._
import _root_.util.{StringUtil, FileUtil, Validations, Keys}
import java.io.File
import java.util._
import model.Account

import scala.collection.mutable.HashSet
import javax.servlet.http._
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketServlet
import org.eclipse.jetty.websocket.WebSocket.Connection
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.commons.io.FileUtils
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import scala.collection.concurrent.Map
import akka.actor._
import scala.concurrent.duration._
import akka.routing._


class ChatServlet extends WebSocketServlet {
  val logger = LoggerFactory.getLogger(classOf[ChatServlet]);

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse):Unit = {
    logger.info(">> doGet")
    println("doGet")
    getServletContext.getNamedDispatcher("default").forward(req, resp)
  }

  override def doWebSocketConnect(req:HttpServletRequest, protocol:String ):ChatWebSocket = {
    logger.info(">> doWebSocketConnect")

    val account = req.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
    val chatRoom = req.getPathInfo.substring(1)
    val user = account.userName

    ChatActorProp.addRouter(chatRoom)
    ChatActorProp.addActor(chatRoom, user)

    val ws = new ChatWebSocket
    ws.chatRoom = chatRoom
    ws.user = account.userName
    ws
  }

  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    super.service(req, res)
  }
}
class ChatActorProp{}
object ChatActorProp{
  private val logger = LoggerFactory.getLogger(classOf[ChatActorProp])
  private val routers = new ConcurrentHashMap[String, Router]
  private val actors = new ConcurrentHashMap[String, ActorRef]
  private val systems = new ConcurrentHashMap[String, ActorSystem]
  def systemRestart(chatRoom: String):ActorSystem = {
    val system = getActorSystem(chatRoom)
    system.shutdown()
    addActorSystem(chatRoom)
  }
  def getActorSystem(chatRoom: String):ActorSystem = {
    if (systems.contains(chatRoom)){
      systems.get(chatRoom)
    }else{
      addActorSystem(chatRoom)
    }
  }
  def addActorSystem(chatRoom: String):ActorSystem = {
    val system = ActorSystem(chatRoom)
    systems.put(chatRoom, system)
    system
  }
  def addActor(chatRoom:String, username:String): ActorRef ={
    if(!actors.containsKey(username)){
      try {
        val chatActor = getActorSystem(chatRoom).actorOf(Props[ChatActor], username)
        actors.put(username, chatActor)
      }catch {
        case e: akka.actor.InvalidActorNameException =>
          val chatActor = systemRestart(chatRoom).actorOf(Props[ChatActor], username)
          actors.put(username, chatActor)
      }
    }
    getActor(chatRoom, username)
  }
  def getActor(chatRoom:String, username:String): ActorRef ={
    if (!actors.containsKey(username)) {
      addActor(chatRoom, username)
    }
    actors.get(username)
  }
  def removeActor(username:String): ActorRef ={
    actors.remove(username)
  }
  def addRouter(chatRoom:String): Router ={
    if(!routers.containsKey(chatRoom)){
      logger.debug(s"No router for ${chatRoom}. Initialize it. ${ChatActorProp.routers}")
      routers.put(chatRoom, Router(BroadcastRoutingLogic()))
    }
    getRouter(chatRoom)
  }
  def getRouter(chatRoom:String): Router ={
    routers.get(chatRoom)
  }
  def addRoutee(chatRoom:String, actor:ActorRef): Unit ={
    val router = getRouter(chatRoom).addRoutee(actor)
    routers.put(chatRoom, router)
  }
  def removeRoutee(chatRoom:String, actor:ActorRef): Unit ={
    val router = getRouter(chatRoom).removeRoutee(actor)
    routers.put(chatRoom, router)
  }
}

class ChatWebSocket extends WebSocket.OnTextMessage{
  protected implicit val jsonFormats: Formats = DefaultFormats
  val logger = LoggerFactory.getLogger(classOf[ChatWebSocket])
  var user:String = _
  var chatRoom:String = _
  var connection:Connection = _
  var chatActor:ActorRef = _

  case class Message(user: String, text: String, kind: String, chatroom: String)
  override def onMessage(data:String) = {
    logger.info(">> onMessage: " + data)

    val message = parse(data).extract[Message]
    val now = new Date

    val userList = for(r <- ChatActorProp.getRouter(chatRoom).routees) yield {
      val str = r.toString
      val username = str.substring(str.lastIndexOf("/") + 1, str.lastIndexOf("#"))
      logger.debug(s"Username from routee:${username}")
      username
    }
    val sb = new StringBuilder
    sb.append(s""""${userList.head}" """)
    for(u <- userList.tail) {
      sb.append(s""","${u}" """)
    }

    val msg = s"""{"user":"${message.user}","text":"${message.text}","chatroom":"${message.chatroom}","kind":"${message.kind}","timestamp":"%tR","members":[${sb.result}]}""".format(now)
    logger.debug("notifyAll:" + msg)
    writeLog(message.text, message.kind, now)
    chatActor ! NotifyAll(msg)
    logger.info("<< onMessage")
  }

  override def onOpen(connection:Connection) = {
    logger.info(">> onOpen")
    chatActor = ChatActorProp.getActor(chatRoom, user)

    this.connection = connection
    connection.setMaxIdleTime(1000 * 60 * 60 * 24)
    chatActor ! Login(this)
  }

  override def onClose(closeCode:Int, message:String) = {
    logger.info(">> onClose")
    writeLog("has left.", "logout", new Date)
    chatActor ! Logout
    ChatActorProp.removeActor(user)
  }

  def writeLog(text:String, kind:String, date:Date): Unit ={
    val dir = new File(_root_.util.Directory.GitBucketHome + "/chat")
    val log = new File(s"${dir.getAbsolutePath}/${chatRoom}.log")
    val timestamp = "%tY/%<tm/%<td %<tR" format date

    val msg = s"""{"user":"${user}","text":"${text}","chatroom":"${chatRoom}","kind":"${kind}","timestamp":"${timestamp}"}""" + System.lineSeparator
    FileUtils.writeStringToFile(log, (msg), "UTF-8", true)
  }
}

case class Login(chatWebSocket: ChatWebSocket)
case class NotifyAll(message: String)
case class SendMessage(message: String)
case object Logout
class ChatActor extends Actor{
  val logger = LoggerFactory.getLogger(classOf[ChatActor]);
  var ws:ChatWebSocket = _

  def receive = {
    case Login(webSocket) => ws = webSocket
      logger.debug(s"${ws.user} logged in room ${ws.chatRoom}")
      ChatActorProp.addRoutee(ws.chatRoom, self)
      logger.debug(s"router:${ChatActorProp.getRouter(ws.chatRoom)}")
    case NotifyAll(message) =>
      logger.info(s"broadcast message:${message}")
      val router = ChatActorProp.getRouter(ws.chatRoom)
      logger.debug(s"router:${router}")
      router.route(SendMessage(message), self)
    case SendMessage(message) =>
      logger.debug(s"send message:${message}")
      try {
        ws.connection.sendMessage(message)
      } catch {
        case e: Exception => logger.error(e.toString);
      }
    case Logout =>
      logger.debug(s"${ws.user} logged out room ${ws.chatRoom}")
      ChatActorProp.removeRoutee(ws.chatRoom, self)
    case ReceiveTimeout => throw new RuntimeException
    case msg => {
      logger.error("Unexpected message: " + msg)
    }
  }
}

