package com.mrredhood.astracode

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class WorkspaceEntry(val documentId:String,val displayName:String,val mimeType:String,val sizeBytes:Long,val lastModifiedMillis:Long,val canWrite:Boolean) {
    val isDirectory:Boolean get()=mimeType==DocumentsContract.Document.MIME_TYPE_DIR
}
internal data class WorkspaceTextPreview(val text:String,val truncated:Boolean)

internal class WorkspaceRepository(context:Context) {
    private val resolver=context.applicationContext.contentResolver
    private val prefs=context.applicationContext.getSharedPreferences("astracode_workspace",Context.MODE_PRIVATE)
    fun savedTreeUri():Uri?=prefs.getString("selected_tree_uri",null)?.let{runCatching{Uri.parse(it)}.getOrNull()}
    fun saveTreeUri(uri:Uri){prefs.edit().putString("selected_tree_uri",uri.toString()).apply()}
    fun clearTreeUri(){prefs.edit().remove("selected_tree_uri").apply()}
    fun rootDocumentId(uri:Uri)=DocumentsContract.getTreeDocumentId(uri)

    fun listChildren(tree:Uri,parentId:String):List<WorkspaceEntry>{
        val childUri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
        val projection=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_FLAGS)
        val cursor=resolver.query(childUri,projection,null,null,null)?:throw IOException("The storage provider did not return a directory listing.")
        val items=cursor.use{c->
            val id=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val name=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mime=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val size=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modified=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flags=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
            buildList{
                while(c.moveToNext()){
                    val f=if(c.isNull(flags))0 else c.getLong(flags).toInt()
                    add(WorkspaceEntry(c.getString(id),c.getString(name)?:"(unnamed)",c.getString(mime)?:"application/octet-stream",if(c.isNull(size))-1L else c.getLong(size),if(c.isNull(modified))0L else c.getLong(modified),f and DocumentsContract.Document.FLAG_SUPPORTS_WRITE!=0))
                }
            }
        }
        return items.sortedWith(compareBy<WorkspaceEntry>{!it.isDirectory}.thenBy{it.displayName.lowercase(Locale.ROOT)})
    }
    fun createDocument(tree:Uri,parentId:String,mime:String,name:String){
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,parentId)
        DocumentsContract.createDocument(resolver,parent,mime,name)?:throw IOException("The provider did not create the item.")
    }
    fun renameDocument(tree:Uri,id:String,name:String){
        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
        DocumentsContract.renameDocument(resolver,uri,name)?:throw IOException("The provider could not rename the item.")
    }
    fun deleteDocument(tree:Uri,id:String){
        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
        if(!DocumentsContract.deleteDocument(resolver,uri))throw IOException("The provider did not delete the item.")
    }
    fun moveDocument(tree:Uri,id:String,sourceParent:String,targetParent:String){
        require(sourceParent!=targetParent){"Choose a different destination folder."}
        val source=DocumentsContract.buildDocumentUriUsingTree(tree,id)
        val from=DocumentsContract.buildDocumentUriUsingTree(tree,sourceParent)
        val to=DocumentsContract.buildDocumentUriUsingTree(tree,targetParent)
        DocumentsContract.moveDocument(resolver,source,from,to)?:throw IOException("This provider does not support moving this item.")
    }
    fun readTextPreview(tree:Uri,id:String):WorkspaceTextPreview{
        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
        val input=resolver.openInputStream(uri)?:throw IOException("The file could not be opened.")
        val output=ByteArrayOutputStream();val buffer=ByteArray(DEFAULT_BUFFER_SIZE);var truncated=false
        input.use{s->while(true){val remaining=MAX_BYTES+1-output.size();if(remaining<=0){truncated=true;break};val read=s.read(buffer,0,minOf(buffer.size,remaining));if(read<0)break;output.write(buffer,0,read);if(output.size()>MAX_BYTES){truncated=true;break}}}
        val raw=output.toByteArray();val bytes=if(raw.size>MAX_BYTES)raw.copyOf(MAX_BYTES)else raw
        return WorkspaceTextPreview(String(bytes,StandardCharsets.UTF_8),truncated)
    }
    fun writeText(tree:Uri,id:String,text:String){
        val bytes=text.toByteArray(StandardCharsets.UTF_8)
        if(bytes.size>MAX_BYTES)throw IOException("The editor can save files up to 256 KiB.")
        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
        val out=resolver.openOutputStream(uri,"wt")?:throw IOException("The provider did not open this file for writing.")
        out.use{it.write(bytes);it.flush()}
    }
    companion object{private const val MAX_BYTES=256*1024}
}
