package com.droxm.browser.engine

import android.webkit.JavascriptInterface

/**
 * Filtros cosméticos (`##`, esconden los huecos y carteles de anuncios). La página llama a este objeto (sincrónico,
 * desde un hilo aparte del WebView) y recibe CSS ya armado por el motor nativo; no hay nada que parsear en JavaScript.
 * Solo devuelve reglas de ocultamiento: no expone datos del usuario.
 */
internal class CosmeticBridge(private val pageUrl: () -> String?) {

    @JavascriptInterface
    fun host(host: String?): String {
        if (host.isNullOrEmpty() || host.length > 253 || !AdBlocker.activeFor(pageUrl())) return ""
        return NativeAdblock.cosmeticForHost(host)
    }

    @JavascriptInterface
    fun names(host: String?, names: String?): String {
        if (host.isNullOrEmpty() || host.length > 253 || names.isNullOrEmpty() || names.length > 200_000) return ""
        if (!AdBlocker.activeFor(pageUrl())) return ""
        return NativeAdblock.cosmeticForNames(host, names)
    }

    companion object {
        const val NAME = "__droxmCosm"

        /**
         * Se inyecta al empezar cada documento (o al terminar de cargar si el WebView no lo soporta; es idempotente).
         * 1) pide el CSS del dominio; 2) junta las clases e ids que aparecen en la página y pide el CSS de las reglas
         * genéricas que les corresponden, con un observador que agrupa los cambios para no gastar batería.
         */
        const val SCRIPT = "(function(){var B=window.$NAME;if(!B||window.__droxmC)return;window.__droxmC=1;" +
            "var h=location.hostname;if(!h)return;" +
            "var st=document.createElement('style');st.id='__droxm_cos';" +
            "function mount(){var p=document.head||document.documentElement;if(p&&!st.parentNode)p.appendChild(st);}" +
            "function add(c){if(c){mount();st.appendChild(document.createTextNode(c));}}" +
            "try{add(B.host(h));}catch(e){}" +
            // Todo está acotado: páginas que cambian el DOM sin parar (reproductores de video, feeds) no deben costar nada.
            "var seen=Object.create(null),total=0,queue=[],timer=0,flushes=0,obs=null;" +
            "function collect(n,out){if(n.nodeType!==1)return;var t=n.tagName;if(t==='VIDEO'||t==='SCRIPT'||t==='CANVAS')return;" +
            "if(n.id&&!seen['#'+n.id]){seen['#'+n.id]=1;out.push('#'+n.id);}" +
            "var c=n.classList;if(c)for(var i=0;i<c.length&&i<10;i++){var k='.'+c[i];if(!seen[k]){seen[k]=1;out.push(k);}}}" +
            "function scan(root,limit){var out=[];collect(root,out);" +
            "var all=root.querySelectorAll?root.querySelectorAll('[class],[id]'):[];" +
            "for(var i=0;i<all.length&&i<limit&&total+out.length<8000;i++)collect(all[i],out);" +
            "if(!out.length)return;total+=out.length;" +
            "for(var j=0;j<out.length;j+=1500){try{add(B.names(h,out.slice(j,j+1500).join('\\n')));}catch(e){}}}" +
            "function flush(){timer=0;var q=queue;queue=[];" +
            "if(document.fullscreenElement||document.webkitFullscreenElement||document.hidden)return;" +
            "flushes++;for(var i=0;i<q.length;i++)scan(q[i],300);" +
            "if(flushes>=30||total>=8000){if(obs)obs.disconnect();}}" +
            "function start(){if(!document.documentElement)return;scan(document.documentElement,3000);" +
            "obs=new MutationObserver(function(ms){for(var i=0;i<ms.length&&queue.length<60;i++){var a=ms[i].addedNodes;" +
            "for(var j=0;j<a.length&&queue.length<60;j++)if(a[j].nodeType===1)queue.push(a[j]);}" +
            "if(queue.length&&!timer)timer=setTimeout(flush,2000);});" +
            "obs.observe(document.documentElement,{childList:true,subtree:true});}" +
            "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();" +
            "})();"
    }
}
