/* 
** Copyright [2013-2015] [Megam Systems]
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package controllers.stack

import scalaz._
import Scalaz._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.collection.immutable.Map
import org.megam.common._
import org.megam.common.amqp._
import org.megam.common.amqp.request._
import org.megam.common.amqp.response._
import org.megam.common.concurrent._
import com.typesafe.config._
import play.api.Logger
import play.api.Play._

/**
 * @author rajthilak
 *
 */
object CloudStandUpPublish {

  def apply = new CloudStandUpPublish(new String(), Map.empty[String, String])

}

case class CloudStandUpPublish(name: String, messages: Map[String,String]) extends MessageContext {

  def queueName = cloudFarm + "_" + MConfig.cloudstandup_queue
  def exchangeName = cloudFarm + "_" + MConfig.cloudstandup_exchange

  val csp_pubMsg = Messages(messages.toList)
  play.api.Logger.debug("%-20s -->[%s]".format("Publish:" + queueName, csp_pubMsg))
  
  def dop(): ValidationNel[Throwable, AMQPResponse] = execute(rmqClient.publish(csp_pubMsg, MConfig.routing_key))
}