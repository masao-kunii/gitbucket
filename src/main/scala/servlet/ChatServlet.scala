package servlet

import java.io.File
import java.util._
import scala.collection.mutable.HashSet
import scala.collection.mutable.SynchronizedSet
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

class ChatServlet extends WebSocketServlet {
  val logger = LoggerFactory.getLogger(classOf[ChatServlet]);
  val clients = new HashSet[ChatWebSocket]

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse):Unit = {
logger.info(">> doGet")
    println("doGet")
    getServletContext.getNamedDispatcher("default").forward(req, resp)
  }

  override def doWebSocketConnect(req:HttpServletRequest, protocol:String ):ChatWebSocket = {
logger.info(">> doWebSocketConnect")
    new ChatWebSocket
  }

  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {

      super.service(req, res)
  }
  protected implicit val jsonFormats: Formats = DefaultFormats
  class ChatWebSocket extends WebSocket.OnTextMessage {
    case class Message(user: String, text: String, kind: String, chatroom: String)

    var connection:Connection = _
    var user:String = ""
    var chatroom:String = ""

    override def onMessage(data:String) = {
logger.info(">> onMessage: " + data)
      val message = parse(data).extract[Message]
      val now = new Date
      val userList = for (c <- clients) yield c.user
      val sb = new StringBuilder
      sb.append(s""""${userList.head}" """)
      for(u <- userList.tail) {
        sb.append(s""","${u}" """)
      }

      val msg = s"""{"user":"${message.user}","text":"${message.text}","chatroom":"${message.chatroom}","kind":"${message.kind}","timestamp":"%tR","members":[${sb.result}]}""".format(now)
      writeLog(message.text, message.kind, now)
//      writeLog(message.user, message.text, message.kind, message.chatroom)
      user = message.user
      chatroom = message.chatroom
      clients.foreach{c =>
        try{
          c.connection.sendMessage(msg)
        }catch{
          case e:Exception =>logger.error(e.toString);
        }
      }
logger.info("<< onMessage")
     }

    override def onOpen(connection:Connection) = {
logger.info(">> onOpen")
      this.connection = connection
      connection.setMaxIdleTime(1000 * 60 * 60 * 24)
      clients add this
     }

    override def onClose(closeCode:Int, message:String) = {
logger.info(">> onClose")
      writeLog("has left.", "logout", new Date)
      clients remove this
     }

    def writeLog(text:String, kind:String, date:Date): Unit ={
//    def writeLog(user:String, text:String, kind:String, chatroom:String): Unit ={
//    def writeLog(msg:String): Unit ={
      val dir = new File(_root_.util.Directory.GitBucketHome + "/chat")
      val log = new File(s"${dir.getAbsolutePath}/${chatroom}.log")
//      val date = new Date
      val timestamp = "%tY/%<tm/%<td %<tR" format date

//      FileUtils.writeStringToFile(log, ("<div class=\"message %s\"><span>%s</span><small> at %tY/%<tm/%<td %<tR</small><p>%s</p></div>" + System.lineSeparator).format(kind, user, date, text), "UTF-8", true)
      val msg = s"""{"user":"${user}","text":"${text}","chatroom":"${chatroom}","kind":"${kind}","timestamp":"${timestamp}"}""" + System.lineSeparator
      FileUtils.writeStringToFile(log, (msg), "UTF-8", true)

    }
  }
}

