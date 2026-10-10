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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat

private data class WorkspaceBreadcrumb(val documentId:String,val name:String)

@Composable
internal fun WorkspaceScreen(){
    val context=LocalContext.current
    val repository=remember(context){WorkspaceRepository(context.applicationContext)}
    val draftStore=remember(context){EditorDraftStore(context.applicationContext)}
    val snapshotStore=remember(context){EditorSnapshotStore(context.applicationContext)}
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
    var draftReady by remember{mutableStateOf(false)}
    var recoveryStatus by remember{mutableStateOf<String?>(null)}
    var snapshotDialog by rememberSaveable{mutableStateOf(false)}
    var snapshotLoading by remember{mutableStateOf(false)}
    var snapshotBusy by remember{mutableStateOf(false)}
    var snapshotNotice by remember{mutableStateOf<String?>(null)}
    var snapshotError by remember{mutableStateOf<String?>(null)}
    var snapshots by remember{mutableStateOf<List<EditorSnapshotSummary>>(emptyList())}
    var pendingDeleteSnapshot by remember{mutableStateOf<EditorSnapshotSummary?>(null)}
    var pendingRestoreSnapshot by remember{mutableStateOf<EditorSnapshotSummary?>(null)}
    var foldViewVisible by rememberSaveable(openedId){mutableStateOf(false)}
    var webPreviewVisible by rememberSaveable(openedId,openedName){mutableStateOf(false)}
    var foldLoading by remember(openedId){mutableStateOf(false)}
    var foldAnalysis by remember(openedId){mutableStateOf<EditorFoldingAnalysis?>(null)}
    var foldedStarts by remember(openedId){mutableStateOf<Set<Int>>(emptySet())}
    var diffView by remember{mutableStateOf<EditorDiffView?>(null)}
    val autosaveConflicts=remember(treeUriString){mutableStateMapOf<String,Boolean>()}
    val workspaceWriteMutex=remember(treeUriString){Mutex()}
    var autosaveMessage by remember{mutableStateOf<String?>(null)}
    var saveImmediately by remember{mutableStateOf(false)}
    var leaveAfterAutosave by remember{mutableStateOf(false)}
    var overwriteDialog by rememberSaveable{mutableStateOf(false)}
    var reloadDialog by rememberSaveable{mutableStateOf(false)}
    var selection by remember{mutableStateOf(TextRange.Zero)}
    var searchVisible by rememberSaveable{mutableStateOf(false)}
    var searchQuery by rememberSaveable{mutableStateOf("")}
    var replacementText by rememberSaveable{mutableStateOf("")}
    var searchMessage by rememberSaveable{mutableStateOf<String?>(null)}
    var loading by remember{mutableStateOf(false)}
    var entries by remember{mutableStateOf<List<WorkspaceEntry>>(emptyList())}
    var dialogMode by rememberSaveable{mutableStateOf<String?>(null)}
    var targetId by rememberSaveable{mutableStateOf<String?>(null)}
    var targetName by rememberSaveable{mutableStateOf("")}
    var nameInput by rememberSaveable{mutableStateOf("")}
    var dialogError by rememberSaveable{mutableStateOf<String?>(null)}
    var closeTabTarget by rememberSaveable{mutableStateOf<String?>(null)}
    var closeTabError by rememberSaveable{mutableStateOf<String?>(null)}
    val editorTabs=remember(treeUriString){mutableStateListOf<WorkspaceEditorTab>()}
    val editorBuffers=remember(treeUriString){mutableStateMapOf<String,WorkspaceEditorBuffer>()}
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

    fun cacheActiveBuffer(){
        val id=openedId?:return
        if(!draftReady)return
        editorBuffers[id]=WorkspaceEditorBuffer(draft,original,selection.start,selection.end,truncated,previewError)
    }
    fun updateDraft(text:String,range:TextRange){
        draft=text;selection=range
        val id=openedId
        if(id!=null&&draftReady)editorBuffers[id]=WorkspaceEditorBuffer(text,original,range.start,range.end,truncated,previewError)
    }
    fun updateSelection(range:TextRange){
        selection=range
        val id=openedId
        if(id!=null&&draftReady)editorBuffers[id]=WorkspaceEditorBuffer(draft,original,range.start,range.end,truncated,previewError)
    }
    fun clearActiveEditor(){
        openedId=null;openedName=null;openedMime=null;openedWritable=false;previewError=null;truncated=false;draft="";original="";draftReady=false;previewLoading=false;recoveryStatus=null;autosaveMessage=null;saveImmediately=false;leaveAfterAutosave=false;selection=TextRange.Zero;searchVisible=false;searchQuery="";replacementText="";searchMessage=null
    }
    fun persistActiveDraft(){
        cacheActiveBuffer()
        val id=openedId?:return
        val treeKey=treeUriString?:return
        val baseline=original;val contents=draft
        if(!draftReady||!openedWritable||truncated||previewError!=null||contents==baseline)return
        scope.launch(Dispatchers.IO){draftStore.save(treeKey,id,baseline,contents)}
    }
    fun backToFiles(){
        persistActiveDraft()
        val id=openedId
        if(id!=null&&dirty&&editable&&autosaveConflicts[id]!=true){
            leaveAfterAutosave=true
            saveImmediately=true
            autosaveMessage="Saving changes before returning to files…"
        }else clearActiveEditor()
    }
    fun closeTabInUi(id:String,discardRecovery:Boolean){
        if(discardRecovery){
            val treeKey=treeUriString
            if(treeKey!=null)scope.launch(Dispatchers.IO){draftStore.delete(treeKey,id)}
        }
        val remaining=WorkspaceTabActions.close(editorTabs.toList(),id)
        editorTabs.clear();editorTabs.addAll(remaining)
        editorBuffers.remove(id)
        if(openedId==id)clearActiveEditor()
    }
    fun activateTab(tab:WorkspaceEditorTab){
        if(openedId==tab.documentId){
            openedName=tab.displayName;openedMime=tab.mimeType;openedWritable=tab.writable
            return
        }
        persistActiveDraft()
        val buffer=editorBuffers[tab.documentId]
        openedId=tab.documentId;openedName=tab.displayName;openedMime=tab.mimeType;openedWritable=tab.writable
        previewError=buffer?.previewError;truncated=buffer?.truncated?:false
        if(buffer!=null){
            draft=buffer.draft;original=buffer.original
            selection=TextRange(buffer.selectionStart.coerceIn(0,buffer.draft.length),buffer.selectionEnd.coerceIn(0,buffer.draft.length))
            draftReady=true;previewLoading=false
            recoveryStatus=if(buffer.draft!=buffer.original)"Draft retained in this session." else null
        }else{
            draft="";original="";selection=TextRange.Zero;draftReady=false;previewLoading=true;recoveryStatus=null
        }
        searchVisible=false;searchQuery="";replacementText="";searchMessage=null;notice=null
        autosaveMessage=when{
            autosaveConflicts[tab.documentId]==true -> "File changed on storage. Autosave is paused to prevent overwriting it."
            buffer!=null&&buffer.draft!=buffer.original -> "Changes save to the workspace after a short pause."
            else -> null
        }
        saveImmediately=false;leaveAfterAutosave=false
    }
    fun openDocument(entry:WorkspaceEntry){
        val requested=WorkspaceEditorTab(entry.documentId,entry.displayName,entry.mimeType,entry.canWrite)
        val next=WorkspaceTabActions.open(editorTabs.toList(),requested)
        if(next==null){notice="Close a tab before opening another. AstraCode keeps up to ${WorkspaceTabActions.MAX_OPEN_TABS} editor tabs.";return}
        editorTabs.clear();editorTabs.addAll(next)
        activateTab(next.first{it.documentId==entry.documentId})
    }
    fun requestCloseActiveTab(){
        val id=openedId?:return
        if(previewLoading){notice="Wait for the file to finish opening before closing its tab.";return}
        if(!draftReady&&isText){
            closeTabInUi(id,discardRecovery=false)
            notice="Closed the tab; any existing local recovery copy was kept."
            return
        }
        cacheActiveBuffer()
        if(draftReady&&isText&&draft!=original){
            closeTabError=null;closeTabTarget=id
        }else closeTabInUi(id,discardRecovery=true)
    }
    fun loadSnapshots(openDialog:Boolean=false){
        val treeKey=treeUriString;val id=openedId
        if(treeKey==null||id==null){snapshotError="Open a file first.";return}
        if(openDialog)snapshotDialog=true
        snapshotLoading=true;snapshotError=null
        scope.launch{
            try{snapshots=withContext(Dispatchers.IO){snapshotStore.list(treeKey,id)}}
            catch(e:Exception){snapshotError=e.message?:"Could not load local snapshots."}
            finally{snapshotLoading=false}
        }
    }
    fun createSnapshot(){
        val treeKey=treeUriString;val id=openedId;val name=openedName.orEmpty();val text=draft
        if(treeKey==null||id==null||!draftReady||!isText||truncated||previewError!=null){snapshotError="This file is not ready for a complete text snapshot.";return}
        scope.launch{
            snapshotBusy=true;snapshotError=null
            val result=withContext(Dispatchers.IO){snapshotStore.create(treeKey,id,name,text)}
            snapshotBusy=false
            when(result){
                EditorSnapshotCreateResult.Created->{snapshotNotice="Snapshot saved locally.";snapshotError=null}
                EditorSnapshotCreateResult.TooLarge->{snapshotNotice=null;snapshotError="Snapshot exceeds the 2 MiB limit."}
                EditorSnapshotCreateResult.Failed->{snapshotNotice=null;snapshotError="Could not save the snapshot on this device."}
            }
            if(snapshotDialog)loadSnapshots()
        }
    }
    fun compareSnapshot(summary:EditorSnapshotSummary){
        val treeKey=treeUriString;val id=openedId;val currentText=draft;val name=openedName.orEmpty()
        if(treeKey==null||id==null)return
        snapshotBusy=true;snapshotError=null
        scope.launch{
            val record=withContext(Dispatchers.IO){snapshotStore.read(treeKey,id,summary.id)}
            if(record==null){
                snapshotError="This snapshot is unavailable or damaged."
            }else{
                val result=withContext(Dispatchers.Default){EditorTextDiff.compare(record.content,currentText)}
                diffView=EditorDiffView(
                    title="Diff · ${summary.fileName}",
                    description="Local snapshot compared with the current editor draft for $name.",
                    leftLabel="Local snapshot",
                    rightLabel="Current draft",
                    result=result,
                )
                snapshotDialog=false
            }
            snapshotBusy=false
        }
    }
    fun compareWorkspaceFile(){
        val uri=tree;val id=openedId;val treeKey=treeUriString;val text=draft;val name=openedName.orEmpty()
        if(uri==null||id==null||treeKey==null||!draftReady)return
        snapshotBusy=true;snapshotError=null
        scope.launch{
            try{
                val stored=withContext(Dispatchers.IO){workspaceWriteMutex.withLock{repository.readTextPreview(uri,id)}}
                if(openedId!=id||treeUriString!=treeKey)return@launch
                if(stored.truncated){
                    snapshotError="The workspace file now exceeds the 2 MiB comparison limit."
                }else{
                    val result=withContext(Dispatchers.Default){EditorTextDiff.compare(stored.text,text)}
                    diffView=EditorDiffView(
                        title="Workspace diff · $name",
                        description="Stored workspace content compared with the current editor draft.",
                        leftLabel="Workspace file",
                        rightLabel="Current draft",
                        result=result,
                    )
                }
            }catch(e:Exception){
                snapshotError=e.message?:"Could not read the current workspace file for comparison."
            }finally{snapshotBusy=false}
        }
    }
    fun requestRestoreSnapshot(summary:EditorSnapshotSummary){
        pendingRestoreSnapshot=summary;snapshotError=null;snapshotDialog=false
    }
    fun restoreSnapshot(summary:EditorSnapshotSummary){
        val treeKey=treeUriString;val id=openedId
        if(treeKey==null||id==null)return
        scope.launch{
            snapshotBusy=true;snapshotError=null
            val record=withContext(Dispatchers.IO){snapshotStore.read(treeKey,id,summary.id)}
            snapshotBusy=false
            if(record==null){
                snapshotError="This snapshot is unavailable or damaged."
            }else if(openedId==id){
                updateDraft(record.content,TextRange.Zero)
                recoveryStatus="Snapshot loaded into the editor draft; use Save file to update the workspace."
                snapshotNotice="Snapshot loaded into draft."
                pendingRestoreSnapshot=null
            }
        }
    }
    fun deleteSnapshot(summary:EditorSnapshotSummary){
        val treeKey=treeUriString;val id=openedId
        if(treeKey==null||id==null)return
        scope.launch{
            snapshotBusy=true;snapshotError=null
            val deleted=withContext(Dispatchers.IO){snapshotStore.delete(treeKey,id,summary.id)}
            snapshotBusy=false
            if(deleted){
                pendingDeleteSnapshot=null
                snapshotNotice="Snapshot deleted."
                loadSnapshots(openDialog=true)
            }else snapshotError="Could not delete this snapshot."
        }
    }
    fun reloadWorkspaceFile(){
        val uri=tree;val id=openedId;val treeKey=treeUriString
        if(uri==null||id==null||treeKey==null)return
        reloadDialog=false
        scope.launch{
            loading=true;autosaveMessage="Reloading workspace file…"
            try{
                val stored=withContext(Dispatchers.IO){workspaceWriteMutex.withLock{repository.readTextPreview(uri,id)}}
                if(openedId!=id||treeUriString!=treeKey)return@launch
                if(stored.truncated){
                    autosaveMessage="Reload blocked: the workspace file exceeds the 2 MiB editor limit."
                }else{
                    draft=stored.text;original=stored.text;selection=TextRange.Zero;draftReady=true
                    editorBuffers[id]=WorkspaceEditorBuffer(stored.text,stored.text,0,0,false,null)
                    autosaveConflicts.remove(id);recoveryStatus=null;autosaveMessage="Reloaded the latest workspace file."
                    withContext(Dispatchers.IO){draftStore.delete(treeKey,id)}
                }
            }catch(e:Exception){
                autosaveMessage="Could not reload the workspace file. ${e.message.orEmpty()}"
            }finally{loading=false}
        }
    }
    fun overwriteWorkspaceFile(){
        val uri=tree;val id=openedId;val treeKey=treeUriString;val text=draft
        if(uri==null||id==null||treeKey==null)return
        overwriteDialog=false
        scope.launch{
            loading=true;autosaveMessage="Overwriting workspace file…"
            try{
                withContext(Dispatchers.IO){workspaceWriteMutex.withLock{repository.writeText(uri,id,text)}}
                if(openedId==id&&treeUriString==treeKey){
                    val latestDraft=draft
                    original=text
                    editorBuffers[id]=WorkspaceEditorBuffer(latestDraft,text,selection.start,selection.end,truncated,previewError)
                    autosaveConflicts.remove(id);recoveryStatus=null
                    autosaveMessage=if(latestDraft==text)"Workspace file overwritten." else "Saved the confirmed version; newer edits remain pending."
                }else{
                    editorBuffers[id]?.let{editorBuffers[id]=it.copy(original=text)}
                    autosaveConflicts.remove(id)
                }
            }catch(e:Exception){
                autosaveMessage="Could not overwrite the workspace file. ${e.message.orEmpty()}"
            }finally{loading=false}
        }
    }
    fun openFoldView(){
        val id=openedId?:return
        if(!draftReady||previewError!=null||truncated)return
        val text=draft
        val fileName=openedName.orEmpty()
        foldLoading=true
        scope.launch{
            val analysis=withContext(Dispatchers.Default){EditorCodeFolding.analyze(fileName,text)}
            if(openedId==id){
                foldAnalysis=analysis
                foldedStarts=emptySet()
                foldViewVisible=true
            }
            foldLoading=false
        }
    }
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
                repository.saveTreeUri(uri);treeUriString=uri.toString();clearActiveEditor()
                moveId=null;moveName=null;moveParent=null;moveDirectory=false;error=null;notice="Workspace selected.";refreshKey++
            }
        }
    }
    LaunchedEffect(treeUriString){
        stack.clear();clearActiveEditor()
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
    LaunchedEffect(treeUriString,openedId,openedName,openedMime,openedWritable){
        val uri=tree;val id=openedId;val treeKey=treeUriString
        if(uri==null||id==null||treeKey==null){previewError=null;previewLoading=false;truncated=false;draftReady=false;return@LaunchedEffect}
        val cached=editorBuffers[id]
        if(cached!=null){
            draft=cached.draft;original=cached.original
            selection=TextRange(cached.selectionStart.coerceIn(0,cached.draft.length),cached.selectionEnd.coerceIn(0,cached.draft.length))
            truncated=cached.truncated;previewError=cached.previewError;draftReady=true;previewLoading=false
            return@LaunchedEffect
        }
        if(!WorkspaceFilePolicy.supportsTextPreview(openedName.orEmpty(),openedMime.orEmpty())){previewError="This file type is not available in the text editor; the file is unchanged.";previewLoading=false;draftReady=false;return@LaunchedEffect}
        previewLoading=true;previewError=null;draftReady=false;recoveryStatus=null
        try{
            val result=withContext(Dispatchers.IO){repository.readTextPreview(uri,id)}
            draft=result.text;original=result.text;truncated=result.truncated;selection=TextRange.Zero
            if(!result.truncated){
                val recovered=withContext(Dispatchers.IO){draftStore.read(treeKey,id)}
                if(recovered!=null){
                    val currentFingerprint=EditorDraftRecordCodec.fingerprint(result.text)
                    when{
                        recovered.baselineFingerprint!=currentFingerprint->{
                            withContext(Dispatchers.IO){draftStore.delete(treeKey,id)}
                            if(openedWritable)recoveryStatus="An old recovery draft was discarded because the file changed on storage."
                        }
                        openedWritable&&recovered.text!=result.text->{
                            draft=recovered.text
                            recoveryStatus="Recovered an unsaved local draft."
                        }
                        recovered.text==result.text->withContext(Dispatchers.IO){draftStore.delete(treeKey,id)}
                    }
                }
            }
            draftReady=true
            editorBuffers[id]=WorkspaceEditorBuffer(draft,original,selection.start,selection.end,truncated,previewError)
        }
        catch(_:SecurityException){previewError="Access denied. Choose the workspace again if permission expired.";draftReady=false}
        catch(e:Exception){previewError="Could not open file. ${e.message?: "The file may no longer be available."}";draftReady=false}
        finally{previewLoading=false}
    }
    LaunchedEffect(treeUriString,openedId,draft,original,draftReady,openedWritable,truncated,previewError){
        val treeKey=treeUriString;val id=openedId
        if(treeKey==null||id==null||!draftReady||!openedWritable||truncated||previewError!=null)return@LaunchedEffect
        if(draft==original){
            withContext(Dispatchers.IO){draftStore.delete(treeKey,id)}
            recoveryStatus=null
            return@LaunchedEffect
        }
        delay(750)
        val result=withContext(Dispatchers.IO){draftStore.save(treeKey,id,original,draft)}
        recoveryStatus=when(result){
            EditorDraftWriteResult.Saved->"Recovery copy saved on this device."
            EditorDraftWriteResult.TooLarge->"Local recovery is unavailable for drafts above 2 MiB."
            EditorDraftWriteResult.Failed->"Could not save a local recovery copy; use Save file."
        }
    }
    LaunchedEffect(treeUriString,openedId,draft,original,draftReady,openedWritable,truncated,previewError,autosaveConflicts[openedId],saveImmediately,leaveAfterAutosave){
        val uri=tree;val id=openedId;val treeKey=treeUriString;val text=draft;val baseline=original
        if(uri==null||id==null||treeKey==null||!draftReady||!openedWritable||truncated||previewError!=null)return@LaunchedEffect
        if(autosaveConflicts[id]==true)return@LaunchedEffect
        if(text==baseline){
            if(leaveAfterAutosave&&openedId==id){leaveAfterAutosave=false;clearActiveEditor()}
            if(saveImmediately)saveImmediately=false
            return@LaunchedEffect
        }
        if(!saveImmediately)delay(900)
        autosaveMessage="Saving to workspace…"
        withContext(NonCancellable+Dispatchers.IO){
            workspaceWriteMutex.withLock{
                val stillCurrent=withContext(Dispatchers.Main.immediate){
                    openedId==id&&treeUriString==treeKey&&draft==text&&original==baseline&&draftReady&&openedWritable&&!truncated&&previewError==null&&autosaveConflicts[id]!=true
                }
                if(stillCurrent){
                    val result=repository.writeTextIfUnchanged(uri,id,baseline,text)
                    withContext(Dispatchers.Main.immediate){
                        when(result){
                            WorkspaceWriteResult.Saved->{
                                val active=openedId==id&&treeUriString==treeKey
                                val buffer=editorBuffers[id]
                                val latestDraft=if(active)draft else buffer?.draft?:text
                                val start=if(active)selection.start else buffer?.selectionStart?:0
                                val end=if(active)selection.end else buffer?.selectionEnd?:0
                                editorBuffers[id]=WorkspaceEditorBuffer(latestDraft,text,start.coerceIn(0,latestDraft.length),end.coerceIn(0,latestDraft.length),buffer?.truncated?:false,buffer?.previewError)
                                autosaveConflicts.remove(id)
                                if(active){
                                    original=text
                                    autosaveMessage=if(latestDraft==text)"Saved automatically to workspace." else "Earlier edits saved; newer edits are pending."
                                    recoveryStatus=null
                                    if(leaveAfterAutosave&&draft==text){leaveAfterAutosave=false;clearActiveEditor()}
                                }
                            }
                            WorkspaceWriteResult.Conflict->{
                                autosaveConflicts[id]=true
                                if(openedId==id){autosaveMessage="File changed on storage. Autosave paused to avoid overwriting external edits.";leaveAfterAutosave=false}
                            }
                            WorkspaceWriteResult.TooLarge->{
                                if(openedId==id){autosaveMessage="Autosave stopped: this draft exceeds the 2 MiB workspace limit.";leaveAfterAutosave=false}
                            }
                            WorkspaceWriteResult.Failed->{
                                if(openedId==id){autosaveMessage="Autosave failed. Check workspace access, then tap Save now to retry.";leaveAfterAutosave=false}
                            }
                        }
                        if(openedId==id&&saveImmediately)saveImmediately=false
                    }
                }
            }
        }
    }
    BackHandler(enabled=openedId!=null||stack.size>1){if(openedId!=null)backToFiles()else if(stack.size>1)stack.removeAt(stack.lastIndex)}

    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(tree!=null&&editorTabs.isNotEmpty()){
            Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                editorTabs.forEach{tab->
                    val buffer=editorBuffers[tab.documentId]
                    val tabDirty=if(tab.documentId==openedId)dirty else buffer!=null&&buffer.draft!=buffer.original
                    val label=tab.displayName+if(tabDirty)" *"else""
                    if(tab.documentId==openedId)Button(onClick={activateTab(tab)},enabled=!loading){Text(label)}
                    else OutlinedButton(onClick={activateTab(tab)},enabled=!loading&&closeTabTarget==null){Text(label)}
                }
            }
        }
        if(tree==null){
            Text("No workspace selected",style=MaterialTheme.typography.titleMedium)
            Text("Choose a folder with Android's system picker. AstraCode saves its granted URI so you can reopen the workspace later.")
            Button(onClick={picker.launch(null)},modifier=Modifier.fillMaxWidth()){Text("Choose project folder")}
        }else if(openedId!=null){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                OutlinedButton(onClick={backToFiles()},modifier=Modifier.weight(1f)){Text("Back to files")}
                OutlinedButton(onClick={requestCloseActiveTab()},enabled=!loading&&!previewLoading,modifier=Modifier.weight(1f)){Text("Close tab")}
            }
            if(dirty){Text("Pending changes",color=MaterialTheme.colorScheme.error);Text(recoveryStatus?:"A local recovery copy is kept while changes are pending.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(autosaveMessage!=null)Text(autosaveMessage.orEmpty(),style=MaterialTheme.typography.bodySmall,color=if(autosaveConflicts[openedId]==true||autosaveMessage.orEmpty().contains("failed",ignoreCase=true))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if(autosaveConflicts[openedId]==true){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                    OutlinedButton(onClick={compareWorkspaceFile()},enabled=!snapshotBusy&&!loading,modifier=Modifier.weight(1f)){Text("Compare changes")}
                    OutlinedButton(onClick={reloadDialog=true},enabled=!loading,modifier=Modifier.weight(1f)){Text("Reload file")}
                }
            }
            Text(openedName.orEmpty(),style=MaterialTheme.typography.titleLarge)
            Text(when{
                !isText->"Unsupported file type."
                truncated->"Read-only: file exceeds the 2 MiB editor limit."
                !openedWritable->"Read-only: storage provider did not grant write support."
                else->"Changes auto-save to the workspace after a short pause."
            },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(previewLoading)CircularProgressIndicator()
            else if(previewError!=null)WorkspaceMessage(previewError.orEmpty(),null){}
            else if(isText){
                if(foldViewVisible && foldAnalysis!=null && !webPreviewVisible){
                    val analysis=foldAnalysis!!
                    Text("Read-only folding view · switch back to Edit source to change text.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${analysis.metrics.lineCount} lines · ${formatSize(analysis.metrics.utf8Bytes.toLong())} · longest line ${analysis.metrics.longestLine} characters",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(analysis.metrics.lineCount>EditorCodeFolding.MAX_FOLD_LINES || analysis.metrics.utf8Bytes>EditorCodeFolding.MAX_ANALYSIS_BYTES){
                        Text("Folding view is capped at ${EditorCodeFolding.MAX_FOLD_LINES} lines and 2 MiB to keep analysis bounded.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }else{
                        val rows=remember(analysis,foldedStarts){EditorCodeFolding.createRows(analysis,foldedStarts)}
                        if(!analysis.syntaxSupported)Text("Brace folding is not enabled for this file type; this view can still inspect its lines.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        else if(analysis.regionsTruncated)Text("Fold analysis reached its 50,000-region or 20,000-level nesting safety limit; remaining lines stay available.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
                        else if(analysis.regions.isEmpty())Text("No multi-line brace-delimited blocks were detected.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(analysis.metrics.lineCount>EditorCodeFolding.PERFORMANCE_NOTICE_LINES || analysis.metrics.longestLine>EditorCodeFolding.PERFORMANCE_NOTICE_LINE_LENGTH){
                            Text("Dense text can take longer to edit on mobile. The folding view is read-only and uses bounded line analysis.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
                        }
                        LazyColumn(modifier=Modifier.fillMaxWidth().heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            items(count=rows.rowCount){rowIndex->
                                val row=rows.rowAt(rowIndex,analysis,draft)
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()){
                                    Text(if(row.placeholder) "…" else (row.lineNumber+1).toString(),modifier=Modifier.width(40.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick={
                                        val start=row.foldStartLine
                                        if(start!=null)foldedStarts=if(start in foldedStarts)foldedStarts-start else foldedStarts+start
                                    },enabled=row.foldStartLine!=null,modifier=Modifier.width(44.dp)){
                                        val collapsed=row.foldStartLine!=null&&row.foldStartLine in foldedStarts
                                        Text(if(row.placeholder||collapsed)"+" else if(row.foldStartLine!=null)"−" else " ")
                                    }
                                    Text(row.text.ifEmpty{" "},modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick={foldViewVisible=false},modifier=Modifier.fillMaxWidth()){Text("Edit source")}
                }else{
                    TextField(value=TextFieldValue(text=draft,selection=selection),onValueChange={value->updateDraft(value.text,value.selection);notice=null;searchMessage=null;recoveryStatus=null},modifier=Modifier.fillMaxWidth().heightIn(min=260.dp),readOnly=!editable,label={Text("File contents")},textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace))
                }
                if(!webPreviewVisible){
                    OutlinedButton(onClick={if(foldViewVisible)foldViewVisible=false else openFoldView()},enabled=draftReady&&!previewLoading&&!foldLoading&&!truncated&&previewError==null,modifier=Modifier.fillMaxWidth()){
                        Text(when{foldLoading->"Analyzing…";foldViewVisible->"Close folding view";else->"Fold / inspect code"})
                    }
                }
                if(WebPreviewPolicy.supports(openedName.orEmpty())){
                    OutlinedButton(
                        onClick={
                            if(webPreviewVisible)webPreviewVisible=false
                            else{webPreviewVisible=true;foldViewVisible=false;searchVisible=false;searchMessage=null}
                        },
                        enabled=draftReady&&!previewLoading&&!truncated&&previewError==null,
                        modifier=Modifier.fillMaxWidth()
                    ){Text(if(webPreviewVisible)"Hide live preview" else "Show live preview")}
                }
                if(webPreviewVisible)WebLivePreviewPane(fileName=openedName.orEmpty(),source=draft)
                OutlinedButton(onClick={searchVisible=!searchVisible;searchMessage=null},enabled=!foldViewVisible&&!webPreviewVisible,modifier=Modifier.fillMaxWidth()){Text(if(searchVisible)"Hide find / replace" else "Find / replace")}
                if(searchVisible&&!foldViewVisible&&!webPreviewVisible){
                    OutlinedTextField(value=searchQuery,onValueChange={searchQuery=it;searchMessage=null},modifier=Modifier.fillMaxWidth(),label={Text("Find (case-insensitive)")},singleLine=true)
                    OutlinedTextField(value=replacementText,onValueChange={replacementText=it},modifier=Modifier.fillMaxWidth(),label={Text("Replace with")},singleLine=true)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                        OutlinedButton(onClick={
                            if(searchQuery.isEmpty())searchMessage="Enter text to find."
                            else{
                                val from=if(selection.start==selection.end)selection.end else maxOf(selection.start,selection.end)
                                val found=EditorTextActions.findNext(draft,searchQuery,from)
                                if(found==null){updateSelection(TextRange.Zero);searchMessage="No matches found."}
                                else{updateSelection(TextRange(found,found+searchQuery.length));searchMessage=null}
                            }
                        },modifier=Modifier.weight(1f)){Text("Find next")}
                        OutlinedButton(onClick={
                            if(searchQuery.isEmpty())searchMessage="Enter text to find."
                            else{
                                val selectedIsMatch=selection.end>selection.start&&selection.end-selection.start==searchQuery.length&&selection.start+searchQuery.length<=draft.length&&draft.regionMatches(selection.start,searchQuery,0,searchQuery.length,ignoreCase=true)
                                val from=if(selectedIsMatch)selection.start else maxOf(selection.start,selection.end)
                                val result=EditorTextActions.replaceNext(draft,searchQuery,replacementText,from)
                                if(result==null)searchMessage="No matches found."
                                else{updateDraft(result.text,TextRange(result.selectionStart,result.selectionEnd));notice=null;recoveryStatus=null;searchMessage="Replaced one match."}
                            }
                        },modifier=Modifier.weight(1f)){Text("Replace match")}
                    }
                    OutlinedButton(onClick={
                        if(searchQuery.isEmpty())searchMessage="Enter text to find."
                        else{
                            val result=EditorTextActions.replaceAll(draft,searchQuery,replacementText)
                            updateDraft(result.text,TextRange(result.selectionStart,result.selectionEnd));notice=null;recoveryStatus=null
                            searchMessage=if(result.replacements==0)"No matches found." else "Replaced ${result.replacements} match(es)."
                        }
                    },modifier=Modifier.fillMaxWidth()){Text("Replace all")}
                    val matchCount=EditorTextActions.countMatches(draft,searchQuery)
                    val selectedMatch=if(searchQuery.isNotEmpty()&&selection.start+searchQuery.length<=draft.length&&selection.end-selection.start==searchQuery.length&&draft.regionMatches(selection.start,searchQuery,0,searchQuery.length,ignoreCase=true))EditorTextActions.matchNumber(draft,searchQuery,selection.start)else null
                    Text(if(searchQuery.isEmpty())"Searches the current draft; changes auto-save after a short pause." else "$matchCount match(es)${selectedMatch?.let{" · match $it"} .orEmpty()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(searchMessage!=null)Text(searchMessage.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(onClick={compareWorkspaceFile()},enabled=draftReady&&!truncated&&isText&&previewError==null&&!snapshotBusy,modifier=Modifier.fillMaxWidth()){Text("Compare workspace file with draft")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                    OutlinedButton(onClick={createSnapshot()},enabled=draftReady&&!truncated&&isText&&previewError==null&&!snapshotBusy,modifier=Modifier.weight(1f)){Text(if(snapshotBusy)"Working…" else "Create snapshot")}
                    OutlinedButton(onClick={loadSnapshots(openDialog=true)},enabled=draftReady&&!truncated&&isText&&previewError==null&&!snapshotBusy,modifier=Modifier.weight(1f)){Text("Snapshots / diff")}
                }
                if(snapshotNotice!=null)Text(snapshotNotice.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                if(snapshotError!=null&&!snapshotDialog)Text(snapshotError.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                if(openedWritable&&!truncated){
                    Button(
                        onClick={if(autosaveConflicts[openedId]==true)overwriteDialog=true else saveImmediately=true},
                        enabled=dirty&&!loading,
                        modifier=Modifier.fillMaxWidth()
                    ){Text(if(loading)"Saving…" else if(autosaveConflicts[openedId]==true)"Overwrite file…" else "Save now")}
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
                            else{openDocument(entry)}
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
            OutlinedButton(onClick={repository.clearTreeUri();treeUriString=null;entries=emptyList();clearActiveEditor();moveId=null;moveName=null;moveParent=null;moveDirectory=false}){Text("Forget workspace")}
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
                else if(deleting&&target!=null)mutate({repository.deleteDocument(uri,target)}){
                    val treeKey=treeUriString
                    if(treeKey!=null)scope.launch(Dispatchers.IO){
                        draftStore.delete(treeKey,target)
                        snapshotStore.deleteAll(treeKey,target)
                    }
                    editorTabs.removeAll{it.documentId==target};editorBuffers.remove(target)
                    if(openedId==target)clearActiveEditor()
                    notice="Deleted $targetName.";dialogMode=null;dialogError=null
                }
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
                            "rename"->if(target==null)dialogError="The selected item is no longer available." else mutate({repository.renameDocument(uri,target,name)}){
                                val index=editorTabs.indexOfFirst{it.documentId==target}
                                if(index>=0)editorTabs[index]=editorTabs[index].copy(displayName=name)
                                if(openedId==target)openedName=name
                                notice="Renamed to $name.";dialogMode=null;dialogError=null
                            }
                        }
                    }
                }
            }) { Text(if (deleting) "Delete" else "Confirm") } },
            dismissButton={TextButton(onClick={dialogMode=null;dialogError=null}){Text("Cancel")}}
        )
    }
    val closingTab=editorTabs.firstOrNull{it.documentId==closeTabTarget}
    if(closingTab!=null)AlertDialog(
        onDismissRequest={closeTabTarget=null;closeTabError=null},
        title={Text("Close tab with unsaved changes?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Keep a local recovery copy for ${closingTab.displayName}, or discard these edits. The workspace file is not changed until Save file is used.")
            if(closeTabError!=null)Text(closeTabError.orEmpty(),color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(onClick={
            val id=closeTabTarget
            val treeKey=treeUriString
            if(id!=null&&treeKey!=null&&openedId==id){
                val baseline=original;val contents=draft
                scope.launch{
                    val result=withContext(Dispatchers.IO){draftStore.save(treeKey,id,baseline,contents)}
                    if(result==EditorDraftWriteResult.Saved){
                        closeTabInUi(id,discardRecovery=false);closeTabTarget=null;closeTabError=null
                        notice="Kept a local recovery draft and closed the tab."
                    }else{
                        closeTabError=if(result==EditorDraftWriteResult.TooLarge)"This draft exceeds the 2 MiB recovery limit. Save the file or discard the draft."else"Could not write a recovery copy. Save the file or discard the draft."
                    }
                }
            }
        }){Text("Keep draft and close")}},
        dismissButton={Row{
            TextButton(onClick={val id=closeTabTarget;closeTabTarget=null;closeTabError=null;if(id!=null)closeTabInUi(id,discardRecovery=true)}){Text("Discard draft")}
            TextButton(onClick={closeTabTarget=null;closeTabError=null}){Text("Cancel")}
        }}
    )
    if(snapshotDialog)AlertDialog(
        onDismissRequest={snapshotDialog=false;snapshotError=null},
        title={Text("Snapshots · ${openedName.orEmpty()}")},
        text={
            Column(modifier=Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Snapshots are local copies on this device. Compare them with the current editor draft or load one back into the draft. Workspace files are only changed by Save file.")
                if(snapshotLoading)CircularProgressIndicator()
                if(snapshotError!=null)Text(snapshotError.orEmpty(),color=MaterialTheme.colorScheme.error)
                if(!snapshotLoading&&snapshots.isEmpty())Text("No snapshots yet. Create a snapshot to record the current draft.")
                snapshots.forEach{snapshot->
                    Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant){
                        Column(modifier=Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Text(snapshot.fileName,style=MaterialTheme.typography.titleSmall)
                            Text(formatSnapshotTime(snapshot.createdAtMillis)+" · "+formatSize(snapshot.contentBytes.toLong()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()){
                                TextButton(onClick={compareSnapshot(snapshot)},enabled=!snapshotBusy&&draftReady,modifier=Modifier.weight(1f)){Text("Compare")}
                                TextButton(onClick={requestRestoreSnapshot(snapshot)},enabled=!snapshotBusy&&draftReady,modifier=Modifier.weight(1f)){Text("Restore")}
                                TextButton(onClick={pendingDeleteSnapshot=snapshot;snapshotDialog=false;snapshotError=null},enabled=!snapshotBusy,modifier=Modifier.weight(1f)){Text("Delete")}
                            }
                        }
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={snapshotDialog=false}){Text("Done")}}
    )
    val restoreTarget=pendingRestoreSnapshot
    if(restoreTarget!=null)AlertDialog(
        onDismissRequest={pendingRestoreSnapshot=null;snapshotError=null},
        title={Text("Restore snapshot to draft?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("This replaces the current in-memory draft for ${openedName.orEmpty()}. It does not save to the workspace file.")
            if(dirty)Text("Your current draft has unsaved edits. Save it or keep a local recovery copy before restoring if you need those edits.",color=MaterialTheme.colorScheme.error)
            if(snapshotError!=null)Text(snapshotError.orEmpty(),color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(onClick={restoreSnapshot(restoreTarget)},enabled=!snapshotBusy){Text("Restore to draft")}},
        dismissButton={TextButton(onClick={pendingRestoreSnapshot=null;snapshotError=null}){Text("Cancel")}}
    )
    val deleteTarget=pendingDeleteSnapshot
    if(deleteTarget!=null)AlertDialog(
        onDismissRequest={pendingDeleteSnapshot=null;snapshotError=null},
        title={Text("Delete snapshot?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Delete the local snapshot from ${formatSnapshotTime(deleteTarget.createdAtMillis)}? This cannot be undone.")
            if(snapshotError!=null)Text(snapshotError.orEmpty(),color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(onClick={deleteSnapshot(deleteTarget)},enabled=!snapshotBusy){Text("Delete snapshot")}},
        dismissButton={TextButton(onClick={pendingDeleteSnapshot=null;snapshotError=null}){Text("Cancel")}}
    )
    val diff=diffView
    if(diff!=null)AlertDialog(
        onDismissRequest={diffView=null},
        title={Text(diff.title)},
        text={
            Column(modifier=Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(diff.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${diff.leftLabel}  →  ${diff.rightLabel}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(diff.result.approximate)Text("Large diff shown as a bounded summary.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                diff.result.lines.forEach{line->
                    Text(line,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,color=when{
                        line.startsWith("+ ") -> MaterialTheme.colorScheme.primary
                        line.startsWith("- ") -> MaterialTheme.colorScheme.error
                        line.startsWith("…") -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
                }
                if(diff.result.omittedLineCount>0)Text("… ${diff.result.omittedLineCount} diff line(s) omitted to keep the view responsive.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
            }
        },
        confirmButton={TextButton(onClick={diffView=null}){Text("Done")}}
    )
    if(overwriteDialog)AlertDialog(
        onDismissRequest={overwriteDialog=false},
        title={Text("Overwrite workspace file?")},
        text={Text("A change was detected on storage after this draft was opened. Overwriting replaces the stored file with your current draft and cannot recover those external edits automatically.")},
        confirmButton={TextButton(onClick={overwriteWorkspaceFile},enabled=!loading){Text("Overwrite file")}},
        dismissButton={TextButton(onClick={overwriteDialog=false}){Text("Cancel")}}
    )
    if(reloadDialog)AlertDialog(
        onDismissRequest={reloadDialog=false},
        title={Text("Reload from storage?")},
        text={Text("Discard the current editor draft and load the latest workspace file. Compare changes first if you need to keep part of your draft.")},
        confirmButton={TextButton(onClick={reloadWorkspaceFile},enabled=!loading){Text("Reload file")}},
        dismissButton={TextButton(onClick={reloadDialog=false}){Text("Keep draft")}}
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
private fun formatSnapshotTime(timestamp:Long):String=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date(timestamp))
private fun formatSize(bytes:Long):String=when{
    bytes<0->"File";bytes<1024->"$bytes B";bytes<1024*1024->String.format(Locale.ROOT,"%.1f KiB",bytes/1024.0)
    else->String.format(Locale.ROOT,"%.1f MiB",bytes/(1024.0*1024.0))
}
