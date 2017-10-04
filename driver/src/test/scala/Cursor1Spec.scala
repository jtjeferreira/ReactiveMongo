import scala.concurrent.Future
import scala.concurrent.duration.{ DurationInt, FiniteDuration, MILLISECONDS }

import reactivemongo.bson.{ BSONDocument, BSONDocumentReader }
import reactivemongo.core.errors.DetailedDatabaseException
import reactivemongo.api.{ Cursor, CursorFlattener, CursorProducer }
import reactivemongo.api.collections.bson.BSONCollection

import org.specs2.concurrent.{ ExecutionEnv => EE }

trait Cursor1Spec { spec: CursorSpec =>
  def group1 = {
    val nDocs = 16517
    s"insert $nDocs records" in { implicit ee: EE =>
      val futs: Seq[Future[Unit]] = for (i <- 0 until nDocs) yield {
        coll.insert(BSONDocument(
          "i" -> i,
          "record" -> s"record$i",
          "junk" -> Seq.fill(100)(s"junk"),
          "junk2" -> Seq.fill(100)(s"junk"))).map(_ => {})
      }

      Future.sequence(futs).map { _ =>
        info(s"inserted $nDocs records")
      } aka "fixtures" must beEqualTo({}).await(1, timeout)
    }

    /*
    { // headOption
      def headOptionSpec(c: BSONCollection, timeout: FiniteDuration) = {
        "find first document when matching" in { implicit ee: EE =>
          c.find(matchAll("headOption1")).cursor().
            headOption must beSome[BSONDocument].await(1, timeout)
        }

        "find first document when not matching" in { implicit ee: EE =>
          c.find(BSONDocument("i" -> -1)).cursor().
            headOption must beNone.await(1, timeout)
        }
      }

      "with the default connection" >> {
        headOptionSpec(coll, timeout)
      }

      "with the default connection" >> {
        headOptionSpec(slowColl, slowTimeout)
      }
    }

    "read one document with success" in { implicit ee: EE =>
      coll.find(matchAll("one")).one[BSONDocument].
        aka("findOne") must beSome[BSONDocument].await(1, timeout)
    }
    */

    def foldSpec1(c: BSONCollection, timeout: FiniteDuration) = {
      "get 10000 first docs" in { implicit ee: EE =>
        c.find(matchAll("cursorspec1")).cursor().collect[List](10000).
          map(_.size) aka "result size" must beEqualTo(10000).await(1, timeout)
      }
      /*
      { // .fold
        "fold all the documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec2")).cursor().fold(0)(
            { (st, _) => debug(s"fold: $st"); st + 1 }).
            aka("result size") must beEqualTo(16517).await(1, timeout)
        }

        "fold only 1024 documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec3")).cursor().
            fold(0, 1024)((st, _) => st + 1).
            aka("result size") must beEqualTo(1024).await(1, timeout)
        }
      }

      { // .foldWhile
        "fold while all the documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec4a")).cursor().foldWhile(0)(
            { (st, _) => debug(s"foldWhile: $st"); Cursor.Cont(st + 1) }).
            aka("result size") must beEqualTo(16517).await(1, timeout)
        }

        "fold while only 1024 documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec5a")).cursor().foldWhile(0, 1024)(
            (st, _) => Cursor.Cont(st + 1)).
            aka("result size") must beEqualTo(1024).await(1, timeout)
        }

        "fold while successfully with async function" >> {
          "all the documents" in { implicit ee: EE =>
            coll.find(matchAll("cursorspec4b")).cursor().foldWhileM(0)(
              (st, _) => Future(Cursor.Cont(st + 1))).
              aka("result size") must beEqualTo(16517).await(1, timeout)
          }

          "only 1024 documents" in { implicit ee: EE =>
            coll.find(matchAll("cursorspec5b")).cursor().foldWhileM(0, 1024)(
              (st, _) => Future.successful(Cursor.Cont(st + 1))).
              aka("result size") must beEqualTo(1024).await(1, timeout)
          }
        }
      }

      { // .foldBulk
        "fold the bulks for all the documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec6a")).cursor().foldBulks(0)({ (st, bulk) =>
            debug(s"foldBulk: $st")
            Cursor.Cont(st + bulk.size)
          }) aka "result size" must beEqualTo(16517).await(1, timeout)
        }

        "fold the bulks for 1024 documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec7a")).cursor().foldBulks(0, 1024)(
            (st, bulk) => Cursor.Cont(st + bulk.size)).
            aka("result size") must beEqualTo(1024).await(1, timeout)
        }

        "fold the bulks with async function" >> {
          "for all the documents" in { implicit ee: EE =>
            coll.find(matchAll("cursorspec6b")).cursor().foldBulksM(0)(
              (st, bulk) => Future(Cursor.Cont(st + bulk.size))).
              aka("result size") must beEqualTo(16517).await(1, timeout)
          }

          "for 1024 documents" in { implicit ee: EE =>
            coll.find(matchAll("cursorspec7b")).cursor().foldBulksM(0, 1024)(
              (st, bulk) => Future.successful(Cursor.Cont(st + bulk.size))).
              aka("result size") must beEqualTo(1024).await(1, timeout)
          }
        }
      }

      { // .foldResponse
        "fold the responses for all the documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec8a")).cursor().foldResponses(0)(
            { (st, resp) =>
              debug(s"foldResponses: $st")
              Cursor.Cont(st + resp.reply.numberReturned)
            }) aka "result size" must beEqualTo(16517).await(1, timeout)
        }

        "fold the responses for 1024 documents" in { implicit ee: EE =>
          c.find(matchAll("cursorspec9a")).cursor().foldResponses(0, 1024)(
            (st, resp) => Cursor.Cont(st + resp.reply.numberReturned)).
            aka("result size") must beEqualTo(1024).await(1, timeout)
        }

        "fold the responses with async function" >> {
          "for all the documents" in { implicit ee: EE =>
            coll.find(matchAll("cursorspec8b")).cursor().foldResponsesM(0)(
              (st, resp) => Future(Cursor.Cont(st + resp.reply.numberReturned))).
              aka("result size") must beEqualTo(16517).await(1, timeout)
          }

          "for 1024 documents" in { implicit ee: EE =>
            coll.find(matchAll("cursorspec9b")).cursor().
              foldResponsesM(0, 1024)(
                (st, resp) => Future(
                  Cursor.Cont(st + resp.reply.numberReturned))).
                aka("result size") must beEqualTo(1024).await(1, timeout)
          }
        }
      }
      */
    }

    "with the default connection" >> {
      foldSpec1(coll, timeout)
    }

    /*
    "with the slow connection" >> {
      foldSpec1(slowColl, slowTimeout)
    }

    "fold the responses with async function" >> {
      "for all the documents" in { implicit ee: EE =>
        coll.find(matchAll("cursorspec8")).cursor().foldResponsesM(0)(
          (st, resp) => Future(Cursor.Cont(st + resp.reply.numberReturned))).
          aka("result size") must beEqualTo(16517).await(1, timeout)
      }

      "for 1024 documents" in { implicit ee: EE =>
        coll.find(matchAll("cursorspec9")).cursor().foldResponsesM(0, 1024)(
          (st, resp) => Future(Cursor.Cont(st + resp.reply.numberReturned))).
          aka("result size") must beEqualTo(1024).await(1, timeout)
      }
    }

    "produce a custom cursor for the results" in { implicit ee: EE =>
      implicit def fooProducer[T] = new CursorProducer[T] {
        type ProducedCursor = FooCursor[T]
        def produce(base: Cursor[T]) = new DefaultFooCursor(base)
      }

      implicit object fooFlattener extends CursorFlattener[FooCursor] {
        def flatten[T](future: Future[FooCursor[T]]) =
          new FlattenedFooCursor(future)
      }

      val cursor = coll.find(matchAll("cursorspec10")).cursor()

      cursor.foo must_== "Bar" and (
        Cursor.flatten(Future.successful(cursor)).foo must_== "raB")
    }

    "throw exception when maxTimeout reached" >> {
      def timeoutSpec(c: BSONCollection, timeout: FiniteDuration)(implicit ee: EE) = {
        def delayedTimeout = FiniteDuration(
          (timeout.toMillis * 1.25D).toLong, MILLISECONDS)

        def futs: Seq[Future[Unit]] = for (i <- 0 until 16517)
          yield c.insert(BSONDocument(
          "i" -> i, "record" -> s"record$i")).
          map(_ => debug(s"fixture #$i inserted (${c.name})"))

        Future.sequence(futs).map(_ => {}).
          aka("fixtures") must beEqualTo({}).await(1, delayedTimeout) and {
            c.find(BSONDocument("record" -> "asd")).maxTimeMs(1).cursor().
              collect[List](10) must throwA[DetailedDatabaseException].
              await(1, timeout + DurationInt(1).seconds)
          }
      }

      "with the default connection" in { implicit ee: EE =>
        timeoutSpec(coll, timeout)
      }

      "with the slow connection" in { implicit ee: EE =>
        timeoutSpec(slowColl, slowTimeout)
      }
    }
    */
  }
}
