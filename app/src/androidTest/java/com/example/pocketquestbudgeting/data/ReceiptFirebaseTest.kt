package com.example.pocketquestbudgeting.data

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptFirebaseTest {
    companion object {
        private val apps = mutableListOf<FirebaseApp>()
        private val stores = mutableListOf<FirebaseFirestore>()
        private val auth = mutableListOf<FirebaseAuth>()
        private val storage = mutableListOf<FirebaseStorage>()
        @BeforeClass @JvmStatic fun connect() {
            repeat(2) { index ->
                val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext<Context>(), FirebaseOptions.Builder()
                    .setProjectId("demo-pocket-quest").setApplicationId("1:123456789012:android:1234567890abcdef123456")
                    .setApiKey("AIzaSyReceiptFixture${index}NotARealKey00000").setStorageBucket("demo-pocket-quest.appspot.com").build(),
                    "receipts-$index-${UUID.randomUUID()}")
                apps += app
                auth += FirebaseAuth.getInstance(app).also { it.useEmulator("10.0.2.2",9099) }
                stores += FirebaseFirestore.getInstance(app).also {
                    it.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                    it.useEmulator("10.0.2.2",8080)
                }
                storage += FirebaseStorage.getInstance(app).also { it.useEmulator("10.0.2.2",9199) }
            }
        }
        @AfterClass @JvmStatic fun close() = runBlocking { auth.forEach { it.signOut() }; stores.forEach { it.terminate().awaitSync() }; apps.forEach { it.delete() } }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.filesDir,"receipt-integration-${UUID.randomUUID()}")
    private lateinit var a: Client
    private lateinit var b: Client
    private class Client(val db: AppDatabase,val account: SyncAccount,val index: Int,val root: File) {
        var attached = true
        val cloud = FirestoreSyncCloud(stores[index])
        val sync = PocketQuestSyncCoordinator(db,cloud,{attached && it==account},{})
        val expenses = ExpenseSync(db,sync)
        val photos = ReceiptSync(db,sync,FirebaseReceiptCloud(stores[index],storage[index]),File(root,"private"))
        suspend fun refresh() { CategorySync(db,sync).refresh(account,cloud);expenses.refresh(account) }
        suspend fun row() = db.expenseDao().getForUser(account.localUserId).single()
        suspend fun send() { assertEquals(SyncRun.COMPLETE,sync.retryPending(account)) }
        suspend fun publish() { photos.runPending(account);send() }
    }
    @Before fun setup() = runBlocking {
        root.mkdirs()
        val username="receipt-${UUID.randomUUID()}@example.test"
        auth[0].createUserWithEmailAndPassword(username,"Synthetic-emulator-only!").awaitSync()
        auth[1].signInWithEmailAndPassword(username,"Synthetic-emulator-only!").awaitSync()
        val uid=requireNotNull(auth[0].currentUser).uid
        suspend fun client(index:Int):Client {
            val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
            if(index==1) {
                val unrelated=db.userDao().createCloudUser("unrelated","Other")
                val category=db.categoryDao().insert(CategoryEntity(userId=unrelated,name="Other"))
                db.expenseDao().insert(ExpenseEntity(userId=unrelated,categoryId=category,amount=1,date="2026-10-01",
                    paymentTime = "00:00",description="Unrelated"))
            }
            val id=db.userDao().createCloudUser(uid,"Synthetic")
            return Client(db,SyncAccount(id,uid,1),index,File(root,"client-$index").apply{mkdirs()})
        }
        a=client(0);b=client(1);a.refresh();b.refresh()
    }
    @After fun finish() { a.db.close();b.db.close();root.deleteRecursively() }
    private fun image(client:Client,label:String,color:Int):File {
        val file=File(client.root,"$label.jpg")
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file
    }
    private suspend fun attach() {
        val category=a.db.categoryDao().getForUser(a.account.localUserId).first()
        a.expenses.save(a.account,ExpenseEntity(userId=a.account.localUserId,categoryId=category.id,amount=9007199254740993L,
            date="2024-02-29",paymentTime = "09:15",description="Synthetic receipt",
            receiptImageUri=image(a,"first",android.graphics.Color.RED).absolutePath))
        a.send();a.publish();b.refresh()
    }
    @Test fun freshClientDownloadedExactBytesAndBothDirectionsReplacedPhotos()=runBlocking {
        attach();val original=a.row();val downloaded=b.row()
        assertNotEquals(original.userId,downloaded.userId);assertNull(downloaded.receiptImageUri)
        assertNotEquals(original.id,downloaded.id)
        val cache=requireNotNull(b.photos.ensureDownloaded(b.account,downloaded.id))
        assertEquals(ReceiptChecks.inspect(File(requireNotNull(original.receiptImageUri))).sha256,ReceiptChecks.inspect(File(cache)).sha256)
        val replacement=image(b,"reverse",android.graphics.Color.BLUE)
        b.expenses.save(b.account,b.row().copy(receiptImageUri=replacement.absolutePath));b.send()
        assertEquals(original.receiptObjectPath,b.row().receiptObjectPath)
        b.publish();a.refresh()
        val reverse=requireNotNull(a.photos.ensureDownloaded(a.account,a.row().id))
        assertEquals(ReceiptChecks.inspect(replacement).sha256,ReceiptChecks.inspect(File(reverse)).sha256)
        val forward=image(a,"forward",android.graphics.Color.GREEN)
        a.expenses.save(a.account,a.row().copy(receiptImageUri=forward.absolutePath));a.send();a.publish();b.refresh()
        val forwardCache=requireNotNull(b.photos.ensureDownloaded(b.account,b.row().id))
        assertEquals(ReceiptChecks.inspect(forward).sha256,ReceiptChecks.inspect(File(forwardCache)).sha256)
        assertEquals(9007199254740993L,a.row().amount)
        repeat(2){a.refresh();b.refresh();b.publish()}
        assertEquals(1,a.db.expenseDao().getForUser(a.account.localUserId).size)
        assertEquals(1,b.db.expenseDao().getForUser(b.account.localUserId).size)
    }
    @Test fun removalKeptTombstoneSafetyAndCleanupRemainedRepeatable()=runBlocking {
        attach();val original=a.row();val path=requireNotNull(original.receiptObjectPath)
        a.expenses.save(a.account,original.copy(receiptImageUri=null),ReceiptChange.REMOVE,path);a.send();a.publish()
        assertNull(a.row().receiptObjectPath);b.refresh();assertNull(b.row().receiptObjectPath)
        repeat(2){a.photos.runPending(a.account)}
        val guard=stores[0].collection("users").document(a.account.uid).collection("receiptObjects").document(path.substringAfterLast('/')).get(Source.SERVER).awaitSync()
        assertEquals("RETIRED",guard.getString("state"))
        a.expenses.delete(a.account,a.row().id);a.send();b.refresh()
        assertTrue(b.db.expenseDao().getForUser(b.account.localUserId).isEmpty())
    }
    @Test fun sameRevisionReplacementsRequiredExplicitReview()=runBlocking {
        attach()
        val nextA=image(a,"a",android.graphics.Color.GREEN);val nextB=image(b,"b",android.graphics.Color.BLUE)
        a.expenses.save(a.account,a.row().copy(receiptImageUri=nextA.absolutePath));a.send();b.refresh()
        b.expenses.save(b.account,b.row().copy(receiptImageUri=nextB.absolutePath));b.send();a.refresh()
        a.photos.runPending(a.account);b.photos.runPending(b.account)
        coroutineScope { val first=async { a.send() };val second=async { b.send() };first.await();second.await() }
        val conflicted=listOf(a,b).single{it.db.syncDao().pending(it.account.uid).any{op->op.state=="CONFLICT"}}
        val operation=conflicted.db.syncDao().pending(conflicted.account.uid).first()
        val remote=requireNotNull(conflicted.db.syncDao().record(conflicted.account.uid,"expenses",operation.recordId))
        conflicted.sync.resolveConflict(conflicted.account,"expenses",operation.recordId,false,remote.revision){conflicted.expenses.applyServer(it)}
        conflicted.refresh()
        assertTrue(conflicted.db.syncDao().pending(conflicted.account.uid).isEmpty())
        assertNotNull(conflicted.photos.ensureDownloaded(conflicted.account,conflicted.row().id))
    }
    @Test fun detachedClientCouldNotInstallCacheIntoRoom()=runBlocking {
        attach();val expense=b.row();b.attached=false
        try{b.photos.ensureDownloaded(b.account,expense.id);fail("Detached cache write accepted")}catch(_:IllegalStateException){}
        assertNull(b.row().receiptImageUri)
    }
}
