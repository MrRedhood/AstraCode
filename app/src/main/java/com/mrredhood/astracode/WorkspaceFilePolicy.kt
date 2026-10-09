package com.mrredhood.astracode

import java.util.Locale

internal object WorkspaceFilePolicy {
    private val textExtensions=setOf("txt","md","markdown","kt","kts","java","xml","json","jsonc","yaml","yml","toml","properties","gradle","html","htm","css","scss","js","mjs","cjs","ts","tsx","jsx","py","sh","bash","zsh","sql","c","h","cpp","hpp","rs","go","swift","dart","ini","conf","env","gitignore","editorconfig")
    fun supportsTextPreview(name:String,mime:String):Boolean{
        if(mime.startsWith("text/"))return true
        if(mime in setOf("application/json","application/xml","application/javascript","application/x-yaml"))return true
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in textExtensions
    }
    fun validateName(raw:String):String?{
        val name=raw.trim()
        if(name.isEmpty())return "Enter a name."
        if(name=="."||name=="..")return "Choose a normal file or folder name."
        if(name.length>120)return "Names must be 120 characters or fewer."
        if(name.any{it=='/'||it=='\\'||it.code<32||it.code==127})return "Names cannot contain path separators or control characters."
        return null
    }
    fun normalizedName(raw:String)=raw.trim()
    fun mimeTypeForNewFile(name:String):String=when(name.substringAfterLast('.', "").lowercase(Locale.ROOT)){
        "html","htm"->"text/html";"json","jsonc"->"application/json";"xml"->"application/xml";"js","mjs"->"application/javascript";"css"->"text/css";"svg"->"image/svg+xml";else->"text/plain"
    }
}
