package com.mrredhood.astracode

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class WorkspaceBreadcrumb(val documentId:String,val name:String)

@Composable
internal fun WorkspaceScreen(){
    val context=LocalContext.current
    val repository=remember(context){WorkspaceRepository(context.applicationContext)}
    val scope=rememberCoroutineScope()
    var treeUriString by rememberSaveable{mutableStateOf(repository.savedTreeUri()?.toString())}
    var error by rememberSaveable{mutableStateOf<String?>(null)}
    var notice by rememberSaveable{mutableStateOf<String?>(null)}
    var refreshKey by rememberSaveable{mutableIntStateOf(0)}
    var query by rememberSaveable{mutableStateOf("")}
    var openedId by rememberSaveable{mutableStateOf<String?>(null)}
    var openedName by rememberSaveable{mutableStateOf<String?>(null)}
    var openedMime by rememberSaveable{mutableStateOf<String?>(null)}
    var openedWritable by rememberSaveable{mutableStateOf(false)}
    var previewError by rememberSaveable{mutableStateOf<String?>(null)}
    var truncated by rememberSaveable{mutableStateOf(false)}
    var previewLoading by remember{mutableStateOf(false)}
    var draft by remember{mutableStateOf("")}
    var original by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(false)}
    var entries by remember{mutableStateOf<List<WorkspaceEntry>>(emptyList())}
    var dialogMode by rememberSaveable{mutableStateOf<String?>(null)}
    var targetId by rememberSaveable{mutableStateOf<String?>(null)}
    var targetName by rememberSaveable{mutableStateOf("")}
    var nameInput by rememberSaveable{mutableStateOf("")}
    var dialogError by rememberSaveable{mutableStateOf<String?>(null)}
    var discardDialog by rememberSaveable{mutableStateOf(false)}
    var moveId by rememberSaveable{mutableStateOf<String?>(null)}
    var moveName by rememberSaveable{mutableStateOf<String?>(null)}
    var moveParent by rememberSaveable{mutableStateOf<String?>(null)}
    var moveDirectory by rememberSaveable{mutableStateOf(false)}
    val stack=remember(treeUriString){mutableStateListOf<WorkspaceBreadcrumb>()}
    val tree=remember(treeUriString){treeUriString?.let(Uri::parse)}
    val current=stack.lastOrNull()
    val isText=WorkspaceFilePolicy.supportsTextPreview(openedName.orEmpty(),openedMime.orEmpty())
    val dirty=isText&&draft!=original
    val editable=isText&&openedWritable&&!truncated&&previewError==null

    fun clearFile(){openedId=null;openedName=null;openedMime=null;openedWritable=false;previewError=null;truncated=false;draft="";original=""}
    fun closeFile(){if(dirty)discardDialog=true else clearFile()}
    fun showAction(mode:String,entry:WorkspaceEntry?=null){dialogMode=mode;targetId=entry?.documentId;targetName=entry?.displayName.orEmpty();nameInput=if(mode=="rename")entry?.displayName.orEmpty()else"";dialogError=null;notice=null}
    fun mutate(action:()->Unit,onSuccess:()->Unit={}){
        if(loading)return
        scope.launch{
            loading=true;error=null;notice=null
            try{withContext(Dispatchers.IO){action()};onSuccess();refreshKey++}
            catch(e:Exception){val message=e.message?:"The storage provider could not complete this operation.";if(dialogMode!=null)dialogError=message else error=message}
            finally{loading=false}
        }
    }

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        if(uri!=null){
            var readGranted=false
            try{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);readGranted=true}
            catch(_:SecurityException){error="Android could not preserve access. Choose the folder again and grant access."}
            if(readGranted){
                runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
                repository.saveTreeUri(uri);treeUriString=uri.toString();clearFile()
                moveId=null;moveName=null;moveParent=null;moveDirectory=false;error=null;notice="Workspace selected.";refreshKey++
            }
        }
    }
    LaunchedEffect(treeUriString){
        stack.clear();clearFile()
        if(tree!=null)try{stack.add(WorkspaceBreadcrumb(repository.rootDocumentId(tree),"Workspace"))}
        catch(_:Exception){error="The saved workspace URI is invalid. Choose the folder again."}
    }
    LaunchedEffect(treeUriString,current?.documentId,refreshKey,openedId){
        val uri=tree;val parent=current
        if(uri==null||parent==null||openedId!=null)return@LaunchedEffect
        loading=true;error=null
        try{entries=withContext(Dispatchers.IO){repository.listChildren(uri,parent.documentId)}}
        catch(_:SecurityException){entries=emptyList();error="Workspace access expired or was revoked. Choose the folder again."}
        catch(e:Exception){entries=emptyList();error="Could not read this folder. ${e.message?: "Check access and retry."}"}
        finally{loading=false}
    }
    LaunchedEffect(treeUriString,openedId,openedName,openedMime){
        val uri=tree;val id=openedId
        if(uri==null||id==null){previewError=null;previewLoading=false;truncated=false;return@LaunchedEffect}
        if(!WorkspaceFilePolicy.supportsTextPreview(openedName.orEmpty(),openedMime.orEmpty())){previewError="This file type is not available in the text editor; the file is unchanged.";return@LaunchedEffect}
        previewLoading=true;previewError=null
        try{val result=withContext(Dispatchers.IO){repository.readTextPreview(uri,id)};draft=result.text;original=result.text;truncated=result.truncated}
        catch(_:SecurityException){previewError="Access denied. Choose the workspace again if permission expired."}
        catch(e:Exception){previewError="Could not open file. ${e.message?: "The file may no longer be available."}"}
        finally{previewLoading=false}
    }
    BackHandler(enabled=openedId!=null||stack.size>1){if(openedId!=null)closeFile()else if(stack.size>1)stack.removeAt(stack.lastIndex)}

    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(tree==null){
            Text("No workspace selected",style=MaterialTheme.typography.titleMedium)
            Text("Choose a folder with Android's system picker. AstraCode saves its granted URI so you can reopen the workspace later.")
            Button(onClick={picker.launch(null)},modifier=Modifier.fillMaxWidth()){Text("Choose project folder")}
        }else if(openedId!=null){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={closeFile()}){Text("Back to files")}
                if(dirty)Text("Unsaved changes",color=MaterialTheme.colorScheme.error)
            }
            Text(openedName.orEmpty(),style=MaterialTheme.typography.titleLarge)
            Text(when{
                !isText->"Unsupported file type."
                truncated->"Read-only: file exceeds the 256 KiB editor limit."
                !openedWritable->"Read-only: storage provider did not grant write support."
                else->"Edit text and use Save file to write changes."
            },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(previewLoading)CircularProgressIndicator()
            else if(previewError!=null)WorkspaceMessage(previewError.orEmpty(),null){}
            else if(isText){
                TextField(value=draft,onValueChange={draft=it;notice=null},modifier=Modifier.fillMaxWidth().heightIn(min=260.dp),readOnly=!editable,label={Text("File contents")},textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace))
                if(openedWritable&&!truncated){
                    Button(onClick={
                        val uri=tree;val id=openedId;val text=draft
                        if(uri!=null&&id!=null)mutate({repository.writeText(uri,id,text)},{original=text;notice="Saved ${openedName.orEmpty()}."})
                    },enabled=dirty&&!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Saving…" else "Save file")}
                }
            }
            if(notice!=null)Text(notice.orEmpty(),color=MaterialTheme.colorScheme.primary)
            if(error!=null)Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
        }else{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                Button(onClick={picker.launch(tree)},modifier=Modifier.weight(1f)){Text("Change folder")}
                OutlinedButton(onClick={refreshKey++},modifier=Modifier.weight(1f)){Text("Refresh")}
            }
            Text(stack.joinToString(" / "){it.name},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                OutlinedButton(onClick={showAction("create-file")},modifier=Modifier.weight(1f)){Text("New file")}
                OutlinedButton(onClick={showAction("create-folder")},modifier=Modifier.weight(1f)){Text("New folder")}
            }
            if(moveId!=null){
                val cycle=moveDirectory&&stack.any{it.documentId==moveId}
                val sameParent=current?.documentId==moveParent
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                    Button(onClick={
                        val uri=tree;val id=moveId;val from=moveParent;val to=current?.documentId
                        if(uri!=null&&id!=null&&from!=null&&to!=null&&!cycle&&!sameParent)mutate(
                            {repository.moveDocument(uri,id,from,to)},
                            {notice="Moved ${moveName.orEmpty()} here.";moveId=null;moveName=null;moveParent=null;moveDirectory=false}
                        )
                    },enabled=!loading&&!cycle&&!sameParent,modifier=Modifier.weight(1f)){Text("Move here")}
                    OutlinedButton(onClick={moveId=null;moveName=null;moveParent=null;moveDirectory=false},modifier=Modifier.weight(1f)){Text("Cancel move")}
                }
                Text(when{cycle->"Choose a folder outside the selected folder.";sameParent->"Choose a different destination folder.";else->"Moving: ${moveName.orEmpty()} — open the destination, then tap Move here."},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),label={Text("Filter this folder")},singleLine=true)
            if(loading)CircularProgressIndicator()
            else if(error!=null)WorkspaceMessage(error.orEmpty(),"Choose folder"){picker.launch(tree)}
            else{
                val visible=entries.filter{it.displayName.contains(query.trim(),ignoreCase=true)}
                if(visible.isEmpty())WorkspaceMessage(if(entries.isEmpty())"This folder is empty." else "No files match your filter.",if(entries.isEmpty())null else "Clear filter"){query=""}
                else{
                    Text("${visible.size} item(s)",style=MaterialTheme.typography.labelMedium)
                    visible.forEach{entry->
                        var menu by remember(entry.documentId){mutableStateOf(false)}
                        Card(onClick={
                            if(entry.isDirectory){stack.add(WorkspaceBreadcrumb(entry.documentId,entry.displayName));query=""}
                            else{openedId=entry.documentId;openedName=entry.displayName;openedMime=entry.mimeType;openedWritable=entry.canWrite;previewError=null;truncated=false;draft="";original="";notice=null}
                        },modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large){
                            Row(modifier=Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                AstraIcon(if(entry.isDirectory)"more"else"code",size=24.dp,description=if(entry.isDirectory)"Folder"else"File")
                                Column(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                    Text(entry.displayName,style=MaterialTheme.typography.titleSmall)
                                    Text(if(entry.isDirectory)"Folder"else formatSize(entry.sizeBytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if(entry.isDirectory)"Open"else if(entry.canWrite)"Edit"else"Preview",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                androidx.compose.foundation.layout.Box{
                                    TextButton(onClick={menu=true},enabled=!loading){Text("Actions")}
                                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                        DropdownMenuItem(text={Text("Rename")},onClick={menu=false;showAction("rename",entry)})
                                        DropdownMenuItem(text={Text("Move")},onClick={menu=false;moveId=entry.documentId;moveName=entry.displayName;moveParent=current?.documentId;moveDirectory=entry.isDirectory;notice="Open a destination folder and tap Move here."})
                                        DropdownMenuItem(text={Text("Delete")},onClick={menu=false;showAction("delete",entry)})
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(notice!=null)Text(notice.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            if(error!=null&&!loading)Text(error.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={repository.clearTreeUri();treeUriString=null;entries=emptyList();clearFile();moveId=null;moveName=null;moveParent=null;moveDirectory=false}){Text("Forget workspace")}
        }
    }

    if(dialogMode!=null){
        val mode=dialogMode.orEmpty();val deleting=mode=="delete"
        AlertDialog(
            onDismissRequest={dialogMode=null;dialogError=null},
            title={Text(when(mode){"create-file"->"Create file";"create-folder"->"Create folder";"rename"->"Rename item";else->"Delete item?"})},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(deleting)Text("Delete “$targetName”? This action cannot be undone.")
                else OutlinedTextField(value=nameInput,onValueChange={nameInput=it;dialogError=null},label={Text("File or folder name")},singleLine=true)
                if(dialogError!=null)Text(dialogError.orEmpty(),color=MaterialTheme.colorScheme.error)
            }},
            confirmButton={TextButton(onClick={
                val uri=tree;val parent=current?.documentId;val target=targetId
                if(uri==null)dialogError="Choose a workspace first."
                else if(deleting&&target!=null)mutate({repository.deleteDocument(uri,target)},{notice="Deleted $targetName.";dialogMode=null;dialogError=null})
                else{
                    val validation=WorkspaceFilePolicy.validateName(nameInput)
                    if(validation!=null)dialogError=validation
                    else{
                        val name=WorkspaceFilePolicy.normalizedName(nameInput)
                        when(mode){
                            "create-file","create-folder"->if(parent==null)dialogError="Choose a destination folder first." else{
                                val mime=if(mode=="create-folder")"vnd.android.document/directory"else WorkspaceFilePolicy.mimeTypeForNewFile(name)
                                mutate({repository.createDocument(uri,parent,mime,name)},{notice="Created $name.";dialogMode=null;dialogError=null;query=""})
                            }
                            "rename"->if(target==null)dialogError="The selected item is no longer available." else mutate({repository.renameDocument(uri,target,name)},{notice="Renamed to $name.";dialogMode=null;dialogError=null})
                        }
                    }
                }
            }){Text(if(deleting)"Delete"else"Confirm")},
            dismissButton={TextButton(onClick={dialogMode=null;dialogError=null}){Text("Cancel")}}
        )
    }
    if(discardDialog)AlertDialog(
        onDismissRequest={discardDialog=false},
        title={Text("Discard unsaved changes?")},
        text={Text("Your changes to ${openedName.orEmpty()} have not been saved.")},
        confirmButton={TextButton(onClick={discardDialog=false;clearFile()}){Text("Discard")}},
        dismissButton={TextButton(onClick={discardDialog=false}){Text("Keep editing")}}
    )
}

@Composable
private fun WorkspaceMessage(message:String,actionLabel:String?,onAction:()->Unit){
    Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant){
        Column(modifier=Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(message,style=MaterialTheme.typography.bodyMedium)
            if(actionLabel!=null)OutlinedButton(onClick=onAction){Text(actionLabel)}
        }
    }
}
private fun formatSize(bytes:Long):String=when{
    bytes<0->"File";bytes<1024->"$bytes B";bytes<1024*1024->String.format(Locale.ROOT,"%.1f KiB",bytes/1024.0)
    else->String.format(Locale.ROOT,"%.1f MiB",bytes/(1024.0*1024.0))
}
