package reactivemongo.api

import scala.concurrent.{ ExecutionContext, Future }

import reactivemongo.core.errors.GenericDatabaseException

import reactivemongo.api.commands._

import reactivemongo.api.indexes.CollectionIndexesManager

/**
 * A mixin that provides commands about this Collection itself.
 *
 * @define createDescription Creates this collection
 * @define autoIndexIdParam If true should automatically add an index on the `_id` field. By default, regular collections will have an indexed `_id` field, in contrast to capped collections. This MongoDB option is deprecated and will be removed in a future release.
 * @define cappedSizeParam the size of the collection (number of bytes)
 * @define cappedMaxParam the maximum number of documents this capped collection can contain
 */
@deprecated("Internal: will be made private", "0.19.8")
trait CollectionMetaCommands { self: Collection =>
  private implicit lazy val unitBoxReader =
    CommandCodecs.unitBoxReader(command.pack)

  private implicit lazy val createWriter = CreateCollection.writer(command.pack)

  /**
   * $createDescription.
   *
   * The returned future will be completed,
   * with an error if this collection already exists.
   *
   * {{{
   * import scala.concurrent.ExecutionContext
   *
   * import reactivemongo.api.CollectionMetaCommands
   * import reactivemongo.api.commands.CommandError
   *
   * def createColl(
   *   coll: CollectionMetaCommands)(implicit ec: ExecutionContext) =
   *   coll.create().recover {
   *     case CommandError.Code(48) => // NamespaceExists
   *       println(s"Collection \\${coll} already exists")
   *   }
   * }}}
   */
  def create()(implicit ec: ExecutionContext): Future[Unit] =
    command.unboxed(self, Create(None, false), ReadPreference.primary)

  /**
   * $createDescription.
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   * import reactivemongo.api.CollectionMetaCommands
   *
   * def createIfNotExists(coll: CollectionMetaCommands)(
   *   implicit ec: ExecutionContext): Future[Unit] =
   *   coll.create(failsIfExists = true)
   * }}}
   *
   * @param failsIfExists if true fails if the collection already exists (default: false)
   */
  def create(@deprecatedName(Symbol("autoIndexId")) failsIfExists: Boolean = false)(implicit ec: ExecutionContext): Future[Unit] = create().recover {
    case CommandError.Code(48 /* already exists */ ) if !failsIfExists => ()

    case CommandError.Message(
      "collection already exists") if !failsIfExists => ()

  }

  /**
   * $createDescription as a capped one.
   *
   * The returned future will be completed with an error if this collection already exists.
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   *
   * def capped(coll: reactivemongo.api.bson.collection.BSONCollection)(
   *   implicit ec: ExecutionContext): Future[Unit] =
   *   coll.createCapped(size = 10, maxDocuments = Some(100))
   * }}}
   *
   * @param size $cappedSizeParam
   * @param maxDocuments $cappedMaxParam
   * @param autoIndexId $autoIndexIdParam
   *
   * @see [[convertToCapped]]
   */
  def createCapped(
    size: Long,
    maxDocuments: Option[Int],
    autoIndexId: Boolean = false)(implicit ec: ExecutionContext): Future[Unit] =
    command.unboxed(
      self,
      Create(Some(Capped(size, maxDocuments)), autoIndexId),
      ReadPreference.primary)

  /**
   * Drops this collection.
   *
   * The returned future will be completed with an error
   * if this collection does not exist.
   */
  @deprecated("Use `drop(Boolean)`", "0.12.0")
  def drop()(implicit ec: ExecutionContext): Future[Unit] =
    drop(true).map(_ => {})

  private implicit lazy val dropWriter = DropCollection.writer(command.pack)

  private implicit lazy val dropReader =
    DropCollectionResult.reader(command.pack)

  /**
   * Drops this collection.
   *
   * If the collection existed and is successfully dropped,
   * the returned future will be completed with true.
   *
   * If `failIfNotFound` is false and the collection doesn't exist,
   * the returned future will be completed with false.
   *
   * Otherwise in case, the future will be completed with the encountered error.
   *
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   * import reactivemongo.api.CollectionMetaCommands
   *
   * def dropIfNotFound(coll: CollectionMetaCommands)(
   *   implicit ec: ExecutionContext): Future[Boolean] =
   *   coll.drop(failIfNotFound = true)
   * }}}
   *
   * @param failIfNotFound the flag to request whether it should fail
   */
  @com.github.ghik.silencer.silent(".*DropCollectionResult.*" /*internal*/ )
  def drop(failIfNotFound: Boolean)(implicit ec: ExecutionContext): Future[Boolean] = {
    command(self, DropCollection, ReadPreference.primary).flatMap {
      case DropCollectionResult(false) if failIfNotFound =>
        Future.failed[Boolean](GenericDatabaseException(
          s"fails to drop collection: $name", Some(26)))

      case DropCollectionResult(dropped) =>
        Future.successful(dropped)
    }
  }

  private implicit lazy val convertWriter = ConvertToCapped.writer(command.pack)

  /**
   * Converts this collection to a [[https://docs.mongodb.com/manual/core/capped-collections/ capped one]].
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   *
   * def capColl(coll: reactivemongo.api.bson.collection.BSONCollection)(
   *   implicit ec: ExecutionContext): Future[Unit] =
   *   coll.convertToCapped(size = 10L, maxDocuments = Some(100))
   * }}}
   *
   * @param size $cappedSizeParam
   * @param maxDocuments $cappedMaxParam
   */
  def convertToCapped(size: Long, maxDocuments: Option[Int])(implicit ec: ExecutionContext): Future[Unit] = command.unboxed(self, ConvertToCapped(Capped(size, maxDocuments)), ReadPreference.primary)

  /**
   * Renames this collection.
   *
   * @param to the new name of this collection
   * @param dropExisting if a collection of name `to` already exists, then drops that collection before renaming this one
   * @return A failure if the dropExisting option is false and the target collection already exists.
   */
  @deprecated(message = "Use `reactivemongo.api.DBMetaCommands.renameCollection on the admin database instead.", since = "0.12.4")
  def rename(to: String, dropExisting: Boolean = false)(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val renameWriter = RenameCollection.writer(command.pack)

    command.unboxed(self.db, RenameCollection(db.name + "." + name, db.name + "." + to, dropExisting), ReadPreference.primary)
  }

  private implicit lazy val statsWriter = CollStats.writer(command.pack)

  private implicit lazy val statsReader = CollStats.reader(command.pack)

  /**
   * Returns various information about this collection.
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   *
   * def isCapped(coll: reactivemongo.api.bson.collection.BSONCollection)(
   *   implicit ec: ExecutionContext): Future[Boolean] =
   *   coll.stats().map(_.capped)
   * }}}
   */
  def stats()(implicit ec: ExecutionContext): Future[CollStatsResult] =
    command(self, CollStats(None), ReadPreference.primary)

  /**
   * Returns various information about this collection.
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   *
   * def getSize(coll: reactivemongo.api.bson.collection.BSONCollection)(
   *   implicit ec: ExecutionContext): Future[Double] =
   *   coll.stats(scale = 1024).map(_.size)
   * }}}
   *
   * @param scale the scale factor (for example, to get all the sizes in kilobytes)
   */
  def stats(scale: Int)(implicit ec: ExecutionContext): Future[CollStatsResult] = command(self, CollStats(Some(scale)), ReadPreference.primary)

  /**
   * Returns an index manager for this collection.
   *
   * {{{
   * import scala.concurrent.{ ExecutionContext, Future }
   * import reactivemongo.api.CollectionMetaCommands
   *
   * def listIndexes(coll: CollectionMetaCommands)(
   *   implicit ec: ExecutionContext): Future[List[String]] =
   *   coll.indexesManager.list().map(_.flatMap { idx =>
   *     idx.name.toList
   *   })
   * }}}
   */
  def indexesManager(implicit ec: ExecutionContext): CollectionIndexesManager.Aux[Serialization.Pack] = CollectionIndexesManager(self.db, name)

  // Command runner
  private lazy val command =
    Command.run(Serialization.internalSerializationPack, failoverStrategy)
}
