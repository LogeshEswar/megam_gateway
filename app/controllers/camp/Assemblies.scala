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
package controllers.camp

import scalaz._
import Scalaz._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.NonEmptyList._
import models._
import models.tosca._
import controllers.Constants.DEMO_EMAIL
import controllers.stack._
import controllers.stack.APIAuthElement
import controllers.funnel.{ FunnelResponse, FunnelResponses }
import controllers.funnel.FunnelErrors._
import org.megam.common.amqp._
import play.api._
import play.api.mvc._
import play.api.mvc.Result

object Assemblies extends Controller with APIAuthElement {

  /*
   * parse.tolerantText to parse the RawBody
   * get requested body and put into the riak bucket
   */
  def post = StackAction(parse.tolerantText) { implicit request =>
    play.api.Logger.debug(("%-20s -->[%s]").format("controllers.Assemblies", "post:Entry"))

    (Validation.fromTryCatchThrowable[Result,Throwable] {
      reqFunneled match {
        case Success(succ) => {
          val freq = succ.getOrElse(throw new Error("Assemblies wasn't funneled. Verify the header."))
          val email = freq.maybeEmail.getOrElse(throw new Error("Email not found (or) invalid."))
          val clientAPIBody = freq.clientAPIBody.getOrElse(throw new Error("Body not found (or) invalid."))
          play.api.Logger.debug(("%-20s -->[%s]").format("controllers.Assemblies", "request funneled."))
          models.tosca.Assemblies.create(email, clientAPIBody) match {
            case Success(asm_succ) => {
                    if (email.trim.equalsIgnoreCase(DEMO_EMAIL)) {
                Status(CREATED)(FunnelResponse(CREATED, """Assemblies initiation dry run submitted successfully.
            |
            |
            |No actual launch in cloud. Signup for a new account to get started.""", "Megam::Assemblies").toJson(true))
              } else {
                /*This isn't correct. Revisit, as the testing progresses.
               We need to trap success/failures.
               */
                asm_succ match {             
                  case Some(asm) =>
                    val req = "{\"cat_id\": \"" + asm.id + "\",\"name\": \"" + asm.name + "\",\"cattype\": \"type\",\"action\": \"create\",\"category\": \"state\"}"
                  models.Requests.createforExistNode(req) match {

                      case Success(succ) =>
                        //val tuple_succ = succ.getOrElse(("Nah", "Gah", "Hah"))
                         val tuple_succ = succ.getOrElse((Map.empty[String, String], "Bah", "nah", "hah", "lah"))

                        (CloudStandUpPublish(tuple_succ._2, tuple_succ._1).dop.flatMap { x =>
                          play.api.Logger.debug(("%-20s -->[%s]").format("controllers.Assemblies", "published successfully."))
                          FunnelResponse(CREATED, """Assemblies initiation instruction submitted successfully.
            |
            |Megam is cranking the cloud for you. It will be ready shortly.""".format(tuple_succ._2, tuple_succ._1).stripMargin, "Megam::Assemblies").successNel[Throwable]
                        } match {
                          //this is only a temporary hack.
                          case Success(succ_cpc) =>
                            Status(CREATED)(FunnelResponse(CREATED, """Request initiation instruction submitted successfully.
            |
            |Check on the node for further updates. It will be ready shortly.""", "Megam::Request").toJson(true))
                          case Failure(err) =>
                            Status(BAD_REQUEST)(FunnelResponse(BAD_REQUEST, """Request initiation submission failed.
            |
            |Retry again, our queue servers are crowded""", "Megam::Request").toJson(true))
                        })
                      case Failure(err) => {
                        val rn: FunnelResponse = new HttpReturningError(err)
                        Status(rn.code)(rn.toJson(true))
                      }
                    }
                  case None =>
                    Status(BAD_REQUEST)(FunnelResponse(BAD_REQUEST, """Assemblies initiation instruction submission failed.
            |
            |Retry again""", "Megam::Assemblies").toJson(true))
                }

              }
            }
            case Failure(err) => {
              val rn: FunnelResponse = new HttpReturningError(err)
              Status(rn.code)(rn.toJson(true))
            }
          }
        }
        case Failure(err) => {
          val rn: FunnelResponse = new HttpReturningError(err)
          Status(rn.code)(rn.toJson(true))
        }
      }
    }).fold(succ = { a: Result => a }, fail = { t: Throwable => Status(BAD_REQUEST)(t.getMessage) })
  }

  /*
   * GET: findById: Show requests for a  node name per user(by email)
   * Email grabbed from header
   * Output: JSON (AssembliesResults)
   **/
  def show(id: String) = StackAction(parse.tolerantText) { implicit request =>
    play.api.Logger.debug(("%-20s -->[%s]").format("controllers.Assemblies", "show:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("nodename", id))

    (Validation.fromTryCatchThrowable[Result,Throwable] {
      reqFunneled match {
        case Success(succ) => {
          val freq = succ.getOrElse(throw new Error("Assemblies wasn't funneled. Verify the header."))
          val email = freq.maybeEmail.getOrElse(throw new Error("Email not found (or) invalid."))
          play.api.Logger.debug(("%-20s -->[%s]").format("controllers.Assemblies", "request funneled."))

          models.tosca.Assemblies.findById(List(id).some) match {
            case Success(succ) =>
              Ok(AssembliesResults.toJson(succ, true))
            case Failure(err) =>
              val rn: FunnelResponse = new HttpReturningError(err)
              Status(rn.code)(rn.toJson(true))
          }
        }
        case Failure(err) => {
          val rn: FunnelResponse = new HttpReturningError(err)
          Status(rn.code)(rn.toJson(true))
        }
      }
    }).fold(succ = { a: Result => a }, fail = { t: Throwable => Status(BAD_REQUEST)(t.getMessage) })

  }

  /*
   * GET: findbyEmail: List all the Assemblies per email
   * Email grabbed from header.
   * Output: JSON (AssembliesResult)
   */
  def list = StackAction(parse.tolerantText) { implicit request =>
    (Validation.fromTryCatchThrowable[Result,Throwable] {
      reqFunneled match {
        case Success(succ) => {
          val freq = succ.getOrElse(throw new Error("Assemblies wasn't funneled. Verify the header."))
          val email = freq.maybeEmail.getOrElse(throw new Error("Email not found (or) invalid."))
          models.tosca.Assemblies.findByEmail(email) match {
            case Success(succ) => Ok(AssembliesResults.toJson(succ, true))
            case Failure(err) =>
              val rn: FunnelResponse = new HttpReturningError(err)
              Status(rn.code)(rn.toJson(true))
          }
        }
        case Failure(err) => {
          val rn: FunnelResponse = new HttpReturningError(err)
          Status(rn.code)(rn.toJson(true))
        }
      }
    }).fold(succ = { a: Result => a }, fail = { t: Throwable => Status(BAD_REQUEST)(t.getMessage) })
  }

}
