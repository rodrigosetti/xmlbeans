/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.newstore2;

import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.DOMImplementation;

// DOM Level 3
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.TypeInfo;

import javax.xml.soap.Detail;
import javax.xml.soap.DetailEntry;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPPart;
import javax.xml.soap.SOAPFaultElement;

import javax.xml.transform.Source;

import java.io.PrintStream;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

import org.apache.xmlbeans.impl.newstore2.Locale.LoadContext;

import org.apache.xmlbeans.impl.newstore2.DomImpl.Dom;
import org.apache.xmlbeans.impl.newstore2.DomImpl.CharNode;
import org.apache.xmlbeans.impl.newstore2.DomImpl.TextNode;
import org.apache.xmlbeans.impl.newstore2.DomImpl.CdataNode;
import org.apache.xmlbeans.impl.newstore2.DomImpl.SaajTextNode;
import org.apache.xmlbeans.impl.newstore2.DomImpl.SaajCdataNode;

import org.apache.xmlbeans.SchemaField;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.QNameSet;

import org.apache.xmlbeans.impl.values.TypeStore;
import org.apache.xmlbeans.impl.values.TypeStoreUser;
import org.apache.xmlbeans.impl.values.TypeStoreVisitor;
import org.apache.xmlbeans.impl.values.TypeStoreUserFactory;

import javax.xml.namespace.QName;

import org.apache.xmlbeans.impl.common.ValidatorListener;

final class Cur
{
    static final int TEMP = 0;
    static final int PERM = 1;
    static final int WEAK = 2;

    static final int TEXT     = 0; // Must be 0
    static final int ROOT     = 1;
    static final int ELEM     = 2;
    static final int ATTR     = 3;
    static final int COMMENT  = 4;
    static final int PROCINST = 5;

    static final int POOLED     = 0;
    static final int UNEMBEDDED = 2;
    static final int EMBEDDED   = 3;
    static final int DISPOSED   = 4;

    private static final int END_POS = -1;
    private static final int NO_POS  = -2;
    
    Cur ( Locale l ) { _locale = l; _pos = NO_POS; }

//    boolean isPositioned ( ) { assert isNormal(); return _xobj != null; }
    boolean isPositioned ( ) { return _xobj != null; }

    static boolean kindIsContainer ( int k ) { return k ==  ELEM || k ==  ROOT; }
    static boolean kindIsFinish    ( int k ) { return k == -ELEM || k == -ROOT; }
    
    int kind ( )
    {
        assert isPositioned();
        return _pos == 0 ? _xobj.kind() : _pos == END_POS ? -_xobj.kind() : TEXT;
    }

    boolean isRoot      ( ) { return _pos == 0 && _xobj.kind() == ROOT;     }
    boolean isElem      ( ) { return _pos == 0 && _xobj.kind() == ELEM;     }
    boolean isAttr      ( ) { return _pos == 0 && _xobj.kind() == ATTR;     }
    boolean isComment   ( ) { return _pos == 0 && _xobj.kind() == COMMENT;  }
    boolean isProcinst  ( ) { return _pos == 0 && _xobj.kind() == PROCINST; }
    
    boolean isText      ( ) { assert isPositioned(); return _pos > 0; }
    boolean isEnd       ( ) { assert isPositioned(); return _pos == END_POS && _xobj.kind() ==ELEM;}
    
    boolean isContainer ( ) { return _pos == 0       && kindIsContainer( _xobj.kind() ); }
    boolean isFinish    ( ) { return _pos == END_POS && kindIsContainer( _xobj.kind() ); }
    boolean isNode      ( ) { assert isPositioned(); return _pos == 0; }
    
    boolean isNormalAttr ( ) { assert isNode(); return _xobj.isNormalAttr(); }
    boolean isXmlns      ( ) { assert isNode(); return _xobj.isXmlns(); }

    QName   getName  ( ) { assert isNode() || isEnd(); return _xobj._name; }
    String  getLocal ( ) { return getName().getLocalPart(); }
    String  getUri   ( ) { return getName().getNamespaceURI(); }

    String  getXmlnsPrefix ( ) { assert isNode() || isXmlns(); return _xobj.getXmlnsPrefix(); }
    String  getXmlnsUri    ( ) { assert isNode() || isXmlns(); return _xobj.getXmlnsUri(); }

    boolean isDomDocRoot  ( ) { return isRoot() && _xobj.getDom() instanceof Document; }
    boolean isDomFragRoot ( ) { return isRoot() && _xobj.getDom() instanceof DocumentFragment; }

    private int cchRight ( ) { assert isPositioned(); return _xobj.cchRight( _pos ); }

    //
    // Creation methods
    //

    private void createHelper ( Xobj xo )
    {
        if (isPositioned())
        {
            Cur from = tempCur( xo, 0 );
            from.moveNode( this );
            from.release();
        }

        moveTo( xo );
    }
    
    void createRoot ( )
    {
        createDomDocFragRoot();
    }
    
    void createDomDocFragRoot ( )
    {
        moveTo( new DocumentFragXobj( _locale ) );
    }
    
    void createDomDocumentRoot ( )
    {
        moveTo( createDomDocumentRootXobj( _locale ) );
    }
    
    static Xobj createDomDocumentRootXobj ( Locale l )
    {
        Xobj xo;

        if (l._saaj == null)
            xo = new DocumentXobj( l );
        else
            xo = new SoapPartDocXobj( l );
        
        if (l._ownerDoc == null)
            l._ownerDoc = xo.getDom();

        return xo;
    }

    void createElement ( QName name )
    {
        createElement( name, null );
    }
    
    void createElement ( QName name, QName parentName )
    {
        createHelper( createElementXobj( _locale, name, parentName ) );
    }

    static Xobj createElementXobj ( Locale l, QName name, QName parentName )
    {
        if (l._saaj != null)
        {
            Class c = l._saaj.identifyElement( name, parentName );

            if (c == SOAPElement.class)       return new SoapElementXobj       ( l, name );
            if (c == SOAPBody.class)          return new SoapBodyXobj          ( l, name );
            if (c == SOAPBodyElement.class)   return new SoapBodyElementXobj   ( l, name );
            if (c == SOAPEnvelope.class)      return new SoapEnvelopeXobj      ( l, name );
            if (c == SOAPHeader.class)        return new SoapHeaderXobj        ( l, name );
            if (c == SOAPHeaderElement.class) return new SoapHeaderElementXobj ( l, name );
            if (c == SOAPFaultElement.class)  return new SoapFaultElementXobj  ( l, name );
            if (c == Detail.class)            return new DetailXobj            ( l, name );
            if (c == DetailEntry.class)       return new DetailEntryXobj       ( l, name );
            if (c == SOAPFault.class)         return new SoapFaultXobj         ( l, name );

            if (c != null)
                throw new IllegalStateException();
        }
        
        return new ElementXobj( l, name );
    }
            
    void createAttr ( QName name )
    {
        createHelper( new AttrXobj( _locale, name ) );
    }
    
    void createComment ( )
    {
        createHelper( createCommentXobj( _locale ) );
    }
    
    void createProcinst ( String target )
    {
        createHelper( new ProcInstXobj( _locale, target ) );
    }
    
    static Xobj createCommentXobj ( Locale l )
    {
        return l._saaj == null ? new CommentXobj( l ) : new SaajCommentXobj( l );
    }

    //
    // General operations
    //

    boolean isSamePos ( Cur that )
    {
        assert isNormal() && that.isNormal();
        return _xobj == that._xobj && _pos == that._pos;
    }
    
    boolean isAtEndOf ( Cur that )
    {
        assert isNormal() && that.isNormal();
        assert that._pos == 0;

        return _xobj == that._xobj && _pos == END_POS;
    }

    boolean isAtEndOfLastPush ( )
    {
        assert _stack != null && _stack.size() > 0;

        return isAtEndOf( (Cur) _stack.get( _stack.size() - 1 ) );
    }

    void setName ( QName name )
    {
        assert isNode() && (_xobj.isElem() || _xobj.isAttr() || _xobj.isProcinst());
        assert name != null;
        
        _xobj._name = name;
        
        _locale._versionAll++;
        _locale._versionSansText++;
    }
    
    private void moveTo ( Xobj x )
    {
        moveTo( x, 0 );
    }
    
    private void moveTo ( Xobj x, int p )
    {
        // This cursor may not be normalized upon entry ...
        
        if (_state == EMBEDDED && x != _xobj)
        {
            _xobj._embedded = listRemove( _xobj._embedded );

            _locale._unembedded = listInsert( _locale._unembedded );
            _state = UNEMBEDDED;
        }

        _xobj = x;
        _pos = p;

        if (_curKind == PERM && _state == UNEMBEDDED && _xobj != null)
        {
            _locale._unembedded = listRemove( _locale._unembedded );
            _xobj._embedded = listInsert( _xobj._embedded );
            _state = EMBEDDED;
        }

        assert isNormal();
    }

    void moveToCur ( Cur to )
    {
        assert isNormal();
        assert to == null || to.isNormal();
        
        if (to == null)
            moveTo( null, NO_POS );
        else
            moveTo( to._xobj, to._pos );
    }

    void moveToDom ( Dom d )
    {
        assert d instanceof Xobj || d instanceof SoapPartDom;
        assert d.locale() == _locale;

        moveTo( d instanceof Xobj ? (Xobj) d : ((SoapPartDom) d)._docXobj );
    }

    void addToSelection ( )
    {
        if (_selection == null)
            _selection = new ArrayList();

        // TODO - make this more efficient ....
        //        Need to add call changeCallback ....
        _selection.add( permCur() );
    }
    
    void removeSelection ( int i )
    {
        assert i >= 0 && i < _selection.size();
        
        _selection.remove( i );
    }

    int selectionCount ( )
    {
        return _selection == null ? 0 : _selection.size();
    }

    void moveToSelection ( int i )
    {
        assert i >= 0 && i < _selection.size();

        moveToCur( (Cur) _selection.get( i ) );
    }

    void clearSelection ( )
    {
        if (_selection != null)
        {
            for ( int i = 0 ; i < _selection.size() ; i++ )
                ((Cur) _selection.get( i )).release();

            _selection.clear();
        }
    }

    void push ( )
    {
        // TODO - make this more efficient ....

        if (_stack == null)
            _stack = new ArrayList();

        _stack.add( permCur() );
    }

    void popButStay ( )
    {
        if (_stack == null || _stack.size() == 0)
            return;
        
        Cur c = (Cur) _stack.remove( _stack.size() - 1 );

        c.release();
    }
    
    boolean pop ( )
    {
        if (_stack == null || _stack.size() == 0)
            return false;
        
        Cur c = (Cur) _stack.remove( _stack.size() - 1 );

        moveToCur( c );

        c.release();

        return true;
    }

    boolean toParent ( )
    {
        return toParent( false );
    }
    
    boolean toParentRaw ( )
    {
        return toParent( true );
    }
    
    boolean hasParent ( )
    {
        assert isPositioned();

        if (_pos == END_POS || (_pos >= 1 && _pos < _xobj.posAfter()))
            return true;

        assert _pos == 0 || _xobj._parent != null;
        
        return _xobj._parent != null;
    }
    
    boolean toParent ( boolean raw )
    {
        assert isPositioned();

        if (_pos == END_POS || (_pos >= 1 && _pos < _xobj.posAfter()))
        {
            moveTo( _xobj );
            return true;
        }

        assert _pos == 0 || _xobj._parent != null;

        if (_xobj._parent != null)
        {
            moveTo( _xobj._parent );
            return true;
        }
        
        if (raw || _xobj.isRoot())
            return false;

        Cur r = _locale.tempCur();
        r.createRoot();
        r.next();
        moveNode( r );
        r.release();

        assert _xobj._parent != null;

        moveTo( _xobj._parent );

        return true;
    }

    boolean hasText ( )
    {
        assert isNode();
        return _xobj.hasText();
    }
    
    boolean hasAttrs ( )
    {
        assert isNode();
        return _xobj._firstChild != null && _xobj._firstChild.isAttr();
    }
    
    boolean hasChildren ( )
    {
        assert isNode();
        return _xobj.hasChildren();
    }
    
    boolean toFirstChild ( )
    {
        assert isNode();

        for ( Xobj x = _xobj._firstChild ; x != null ; x = x._nextSibling )
        {
            if (!x.isAttr())
            {
                moveTo( x );
                return true;
            }
        }

        return false;
    }
    
    protected boolean toLastChild ( )
    {
        assert isNode();

        if (_xobj._lastChild == null || _xobj._lastChild.isAttr())
            return false;

        moveTo( _xobj._lastChild );

        return true;
    }

    boolean toNextSibling ( )
    {
        assert isNode();

        if (_xobj.isAttr())
        {
            if (_xobj._nextSibling != null && _xobj._nextSibling.isAttr())
            {
                moveTo( _xobj._nextSibling );
                return true;
            }
        }
        else if (_xobj._nextSibling != null)
        {
            moveTo( _xobj._nextSibling );
            return true;
        }

        return false;
    }

    boolean toFirstAttr ( )
    {
        assert isNode();

        if (_xobj._firstChild == null || !_xobj._firstChild.isAttr())
            return false;

        moveTo( _xobj._firstChild );

        return true;
    }
    
    boolean toLastAttr ( )
    {
        assert isNode();

        if (!toFirstAttr())
            return false;

        while ( toNextAttr() )
            ;

        return true;
    }
    
    boolean toNextAttr ( )
    {
        assert isAttr();

        if (_xobj._nextSibling == null || !_xobj._nextSibling.isAttr())
            return false;
        
        moveTo( _xobj._nextSibling );

        return true;
    }
    
    boolean toPrevAttr ( )
    {
        assert isAttr();
        
        if (_xobj._prevSibling == null || !_xobj._prevSibling.isAttr())
            return false;
        
        moveTo( _xobj._prevSibling );

        return true;
    }
    
    void toEnd ( )
    {
        assert isNode();
        moveTo( _xobj, END_POS );
    }
    
    void moveToCharNode ( CharNode node )
    {
        assert node._src instanceof Dom;

        moveToDom( (Dom) node._src );

        CharNode n;

        n = _xobj._charNodesValue =
            updateCharNodes( _locale, _xobj, _xobj._charNodesValue, _xobj._cchValue );
        
        for ( ; n != null ; n = n._next )
        {
            if (node == n)
            {
                moveTo( getNormal( _xobj, n._off + 1 ), _posTemp );
                return;
            }
        }

        n = _xobj._charNodesAfter =
            updateCharNodes( _locale, _xobj, _xobj._charNodesAfter, _xobj._cchAfter );

        for ( ; n != null ; n = n._next )
        {
            if (node == n)
            {
                moveTo( getNormal( _xobj, n._off + _xobj._cchValue + 2 ), _posTemp );
                return;
            }
        }

        assert false;
    }
    
    boolean prev ( )
    {
        assert isPositioned();

        if (_xobj.isRoot() && _pos == 0)
            return false;
        
        Xobj x = getDenormal();
        int  p = _posTemp;

        assert p > 0 && p != END_POS;

        int pa = x.posAfter();

        if (p > pa)
            p = pa;
        else if (p == pa)
        {
            //
            // Text after an attr is allowed only on the last attr,
            // and that text belongs to the parent container..  
            //
            // If we're a thte end of the last attr, then we were just
            // inside the container, and we need to skip the attrs.
            //
            
            if (x.isAttr() &&
                (x._cchAfter > 0 || x._nextSibling == null || !x._nextSibling.isAttr()))
            {
                x = x.ensureParent();
                p = 0;
            }
            else
                p = END_POS;
        }
        else if (p == pa - 1)
        {
            p = x._cchValue > 0 ? 1 : 0;
        }
        else if (p > 1)
            p = 1;
        else
        {
            assert p == 1;
            p = 0;
        }
        
        moveTo( getNormal( x, p ), _posTemp );

        return true;
    }
    
    boolean nextWithAttrs ( )
    {
        int k = kind();

        if (kindIsContainer( k ))
        {
            if (toFirstAttr())
                return true;
        }
        else if (k == -ATTR)
        {
            if (next())
                return true;
            
            toParent();
            
            if (!toParentRaw())
                return false;
        }
        
        return next();
    }
    
    boolean next ( )
    {
        assert isNormal();

        Xobj x = _xobj;
        int  p = _pos;

        int pa = x.posAfter();

        if (p >= pa)
            p = _xobj.posMax();
        else if (p == END_POS)
        {
            if (x.isRoot() || (x.isAttr() && (x._nextSibling == null || !x._nextSibling.isAttr())))
                return false;
            
            p = pa;
        }
        else if (p > 0)
        {
            assert x._firstChild == null || !x._firstChild.isAttr();

            if (x._firstChild != null)
            {
                x = x._firstChild;
                p = 0;
            }
            else
                p = END_POS;
        }
        else
        {
            assert p == 0;
            
            p = 1;
            
            if (x._cchValue == 0)
            {
                if (x._firstChild != null)
                {
                    if (x._firstChild.isAttr())
                    {
                        Xobj a = x._firstChild;

                        while ( a._nextSibling != null && a._nextSibling.isAttr() )
                            a = a._nextSibling;

                        if (a._cchAfter > 0)
                        {
                            x = a;
                            p = a.posAfter();
                        }
                        else if (a._nextSibling != null)
                        {
                            x = a._nextSibling;
                            p = 0;
                        }
                    }
                    else
                    {
                        x = x._firstChild;
                        p = 0;
                    }
                }
            }
        }

        moveTo( getNormal( x, p ), _posTemp );

        return true;
    }

    int nextChars ( int cch )
    {
        assert isPositioned();
        
        int cchRight = cchRight();

        if (cchRight == 0)
            return 0;

        if (cch < 0 || cch >= cchRight)
        {
            // Use next to not skip over children
            next();
            return cchRight;
        }

        moveTo( getNormal( _xobj, _pos + cch ), _posTemp );
        
        return cch;
    }

    void setCharNodes ( CharNode nodes )
    {
        assert isPositioned();
        assert !_xobj.isRoot() || _pos > 0;
        
        Xobj x = getDenormal();
        int  p = _posTemp;

        if (p >= x.posAfter())
            x._charNodesAfter = nodes;
        else
            x._charNodesValue = nodes;

        for ( ; nodes != null ; nodes = nodes._next )
            nodes._src = x;
    }

    CharNode getCharNodes ( )
    {
        assert isPositioned();
        assert !_xobj.isRoot() || _pos > 0;
        
        Xobj x = getDenormal();
        int  p = _posTemp;

        CharNode nodes;

        if (p >= x.posAfter())
        {
            nodes = x._charNodesAfter =
                updateCharNodes( _locale, x, x._charNodesAfter, x._cchAfter );
        }
        else
        {
            nodes = x._charNodesValue =
                updateCharNodes( _locale, x, x._charNodesValue, x._cchValue );
        }

        return nodes;
    }
    
    private static CharNode updateCharNodes ( Locale l, Object src, CharNode nodes, int cch )
    {
        CharNode node = nodes;
        int i = 0;

        while ( node != null && cch > 0 )
        {
            assert node._src == src;

            if (node._cch > cch)
                node._cch = cch;

            node._off = i;
            i += node._cch;
            cch -= node._cch;

            node = node._next;
        }

        if (cch <= 0)
        {
            for ( ; node != null ; node = node._next )
            {
                assert node._src == src;

                if (node._cch != 0)
                    node._cch = 0;

                node._off = i;
            }
        }
        else
        {
            node = l.createTextNode();
            node._src = src;
            node._cch = cch;
            node._off = i;
            nodes = CharNode.appendNode( nodes, node );
        }

        return nodes;
    }

    final String namespaceForPrefix ( String prefix )
    {
        assert isContainer();
        
        if (prefix == null)
            prefix = "";
        
        if (prefix.equals( "xml" ))
            return Locale._xml1998Uri;

        if (prefix.equals( "xmlns" ))
            return Locale._xmlnsUri;
        
        for ( Xobj c = _xobj ; c != null ; c = c._parent )
            for ( Xobj a = c._firstChild ; a != null && a.isAttr() ; a = a._nextSibling )
                if (a.isXmlns() && a.getXmlnsPrefix().equals( prefix ))
                    return a.getXmlnsUri();

        return null;
    }

    final String prefixForNamespace ( String ns )
    {
        throw new RuntimeException( "Not implemented" );
    }

    boolean ancestorOf ( Cur that )
    {
        assert isNode() && that.isPositioned();

        if (_xobj == that._xobj &&
            (that._pos == END_POS || (that._pos > 0 && that._pos < _xobj.posAfter())))
        {
                return true;
        }
        
        if (_xobj._firstChild == null)
            return false;

        for ( Xobj x = that._xobj ; x != null ; x = x._parent )
            if (x == _xobj)
                return true;

        return false;
    }
    
    void moveNode ( Cur to )
    {
        assert isNode() && !isRoot();
        assert to == null || to.isPositioned();
        assert to == null || !ancestorOf( to );
        assert to == null || (!to.isNode() || !to.isRoot());
        
        // TODO - this code may not handle targets near attributes
        // perfectly ... 

        // We're moveing this node, if there is anyu text after it,
        // move this text to before this node.
        
        if (_xobj.cchAfter() > 0)
        {
            Cur fromChars = tempCur( _xobj, _xobj.posAfter() );
            fromChars.moveChars( this, _xobj.cchAfter() );
            fromChars.release();
        }
        
        assert _xobj.cchAfter() == 0;

        _xobj.removeXobj();

        if (to != null)
        {
            Object srcRight = null;
            int cchRight = to.cchRight();
            
            if (cchRight > 0)
            {
                srcRight = to.moveChars( null, cchRight );
                assert cchRight == to._cchSrc;
            }

            assert to._pos == 0 || to._pos == END_POS;

            if (to._pos == 0)
                to._xobj.insertXobj( _xobj );
            else
                to._xobj.appendXobj( _xobj );

            if (srcRight != null)
            {
                Cur toChars = tempCur( _xobj, _xobj.posAfter() );
                toChars.insertChars( srcRight, to._offSrc, cchRight );
                toChars.release();
            }
        }

        // todo - make a callback to master to do this work in addition
        // to providing a notification thast a chnage will take place
        // .. will have to make the call earler. ...
        
        _locale._versionAll++;
        _locale._versionSansText++;
    }
    
    Object moveChars ( Cur to, int cchMove )
    {
        assert isPositioned();
        assert cchMove == 0 || isText();
        assert to == null || to.isNormal();
        assert cchMove >= 0 && cchMove <= cchRight();
        
        if (cchMove == 0)
        {
            _cchSrc = 0;
            _offSrc = 0;
            return null;
        }

        if (to == null)
        {
            // If there is a cursor in the sequence of chars to remove,
            // then create a new place for these chars to live and move
            // them there, taking the cursors with them.
            
            for ( Cur e = _xobj.getEmbedded() ; e != null ; e = e._next )
            {
                if (e != this && inChars( e, cchMove ))
                {
                    e = _locale.tempCur();
                    
                    e.createRoot();
                    e.next();
                    
                    Object chars = moveChars( e, cchMove );
                    
                    e.release();
                    
                    return chars;
                }
            }
        }
        else
        {
            int pa = _xobj.posAfter();

            // Check for no-op, but return the text "moved"
            
            if (_xobj == to._xobj && inChars( to, cchMove ))
            {
                Object src;

                if (_pos >= pa)
                {
                    src = _xobj._srcAfter;
                    _offSrc = _xobj._offAfter + _pos - pa;
                }
                else
                {
                    src = _xobj._srcValue;
                    _offSrc = _xobj._offValue + _pos - 1;
                }

                moveTo( _xobj, _pos + cchMove );

                _cchSrc = cchMove;
                
                return src;
            }

            if (_pos < pa)
                to.insertChars( _xobj._srcValue, _xobj._offValue + _pos - 1, cchMove );
            else
                to.insertChars( _xobj._srcAfter, _xobj._offAfter + _pos - pa, cchMove );
        }

        Object srcMoved;
        int    offMoved;
        
        int pa = _xobj.posAfter();
        
        if (_pos < pa)
        {
            int i = _pos - 1;
            
            srcMoved = _xobj._srcValue;
            offMoved = _xobj._offValue + i;

            _xobj._srcValue =
                _locale._charUtil.removeChars(
                    i, cchMove,
                    _xobj._srcValue, _xobj._offValue, _xobj._cchValue );

            _xobj._offValue = _locale._charUtil._offSrc;
            _xobj._cchValue = _locale._charUtil._cchSrc;
        }
        else
        {
            int i = _pos - pa;
            
            srcMoved = _xobj._srcAfter;
            offMoved = _xobj._offAfter + i;

            _xobj._srcAfter =
                _locale._charUtil.removeChars(
                    i, cchMove,
                    _xobj._srcAfter, _xobj._offAfter, _xobj._cchAfter );

            _xobj._offAfter = _locale._charUtil._offSrc;
            _xobj._cchAfter = _locale._charUtil._cchSrc;
        }
        
        for ( Cur e = _xobj.getEmbedded() ; e != null ; e = e._next )
            if (e != this && inChars( e, cchMove ))
                e.moveTo( to._xobj, to._pos + e._pos - _pos );

        // The case where I delete all value text, _pos will be at end of node,
        // need to normalize to the first child (if any)
        
        if (_pos == _xobj.posAfter() - 1 && _xobj._firstChild != null)
            moveTo( getNormal( _xobj._firstChild, 0 ), _posTemp );
        else
            moveTo( getNormal( _xobj, _pos ), _posTemp );

        _locale._versionAll++;

        _offSrc = offMoved;
        _cchSrc = cchMove;

        return srcMoved;
    }

    void insertChars ( Object src, int off, int cch )
    {
        assert isNormal() && cch >= 0;
        assert !isRoot();

        if (cch > 0)
        {
            Xobj x = getDenormal();
            int  p = _posTemp;

            assert p > 0;

            for ( Cur e = x.getEmbedded() ; e != null ; e = e._next )
                if (e != this && e._pos >= p)
                    e._pos += cch;

            if (p >= x.posAfter())
            {
                x._srcAfter =
                    _locale._charUtil.insertChars(
                        p - x.posAfter(),
                        x._srcAfter, x._offAfter, x._cchAfter, src, off, cch );
                
                x._offAfter = _locale._charUtil._offSrc;
                x._cchAfter = _locale._charUtil._cchSrc;
            }
            else
            {
                x._srcValue =
                    _locale._charUtil.insertChars(
                        p - 1,
                        x._srcValue, x._offValue, x._cchValue, src, off, cch );
                
                x._offValue = _locale._charUtil._offSrc;
                x._cchValue = _locale._charUtil._cchSrc;
            }
            
            _locale._versionAll++;
        }
    }

    protected final void setBookmark ( Class c, Object o )
    {
        assert isNormal();
        assert c != null;
        assert o == null || o.getClass() == c;

        for ( Cur x = _xobj._embedded ; x != null ; x = x._next )
        {
            if (x._pos == _pos && x._obj != null && x._obj.getClass() == c)
            {
                if (o == null)
                    x.release();
                else
                    x._obj = o;

                return;
            }
        }

        Cur cur = _locale.permCur();

        cur.moveToCur( this );

        assert cur._obj == null;

        cur._obj = o;
    }
    
    final Object getBookmark ( Class c )
    {
        assert isNormal();
        assert c != null;

        for ( Cur x = _xobj._embedded ; x != null ; x = x._next )
            if (x._pos == _pos && x._obj != null && x._obj.getClass() == c)
                return x._obj;
        
        return null;
    }
    
    String getString ( int cch )
    {
        assert isNormal() && _xobj != null;
        
        return _xobj.getString( _pos, cch );
    }

    String getValueString ( )
    {
        assert isNode();
        
        // TODO - make sure there are no children (ok for an element to have
        // attrs)

        return _xobj.getString( 1, _xobj._cchValue );
    }

    Object getChars ( int cch )
    {
        assert isPositioned();

        return _xobj.getChars( _pos, cch, this );
    }
    
    Object getValueChars ( )
    {
        assert isNode();
        
        return _xobj.getChars( 1, -1, this );
    }
    
    void copyNode ( Cur cTo )
    {
        // TODO - make moveNode, moveChars, etc, deal with targeting different
        // locals -- may have to copy instead of move .....

        assert cTo != null;
        assert isNode();

        Xobj newParent = null;
        Xobj copy = null;
        Xobj xo = _xobj;
            
        walk:
        for ( ; ; )
        {
            Xobj newXo = xo.newNode();

            newXo._srcValue = xo._srcValue;
            newXo._offValue = xo._offValue;
            newXo._cchValue = xo._cchValue;
            
            newXo._srcAfter = xo._srcAfter;
            newXo._offAfter = xo._offAfter;
            newXo._cchAfter = xo._cchAfter;

            // TODO - strange to have charNode stuff inside here .....
            newXo._charNodesValue = CharNode.copyNodes( xo._charNodesValue, newXo );
            newXo._charNodesAfter = CharNode.copyNodes( xo._charNodesAfter, newXo );

            if (newParent == null)
                copy = newXo;
            else
                newParent.appendXobj( newXo );

            if (xo._firstChild != null)
            {
                newParent = newXo;
                xo = xo._firstChild;
                continue;
            }
            
            if (xo._nextSibling == null)
            {
                do
                {
                    if (xo == _xobj)
                        break walk;
                    
                    xo = xo._parent;
                    newParent = newParent._parent;

                    if (xo == _xobj)
                        break walk;
                }
                while ( xo._nextSibling == null );
            }
            else if (xo == _xobj)
                break walk;
            
            xo = xo._nextSibling;
        }

        copy._srcAfter = null;
        copy._offAfter = 0;
        copy._cchAfter = 0;

        if (cTo._xobj == null)
            cTo.moveTo( copy );
        else
        {
            // TODO - how to operate between mcur and fcur
            
            Cur from = cTo._locale.tempCur();
            from.moveNode( cTo );
            from.release();
        }
    }

    Cur weakCur ( Object o )
    {
        Cur c = _locale.weakCur( o );
        c.moveToCur( this );
        return c;
    }

    Cur tempCur ( )
    {
        Cur c = _locale.tempCur();
        c.moveToCur( this );
        return c;
    }

    Cur permCur ( )
    {
        Cur c = _locale.permCur();
        c.moveToCur( this );
        return c;
    }

    private Cur tempCur ( Xobj x, int p )
    {
        assert x != null || p == NO_POS;

        Cur c = _locale.tempCur();

        if (x == null)
            c.moveTo( null );
        else
            c.moveTo( getNormal( x, p ), _posTemp );
        
        return c;
    }

    // Is s a cursor in the chars defined by cch chars after where this
    // is positioned.
    
    private boolean inChars ( Cur c, int cch )
    {
        assert isPositioned() && isText() && cchRight() >= cch;
        assert c.isPositioned();

        return c._xobj != _xobj || c._pos < 0 ? false : c._pos >= _pos && c._pos < _pos + cch;
    }

    private Xobj getNormal ( Xobj x, int p )
    {
        assert p == END_POS || (p >= 0 && p <= x.posMax());
        
        if (p == x.posMax())
        {
            if (x._nextSibling != null)
            {
                x = x._nextSibling;
                p = 0;
            }
            else
            {
                x = x.ensureParent();
                p = END_POS;
            }
        }
        else if (p == x.posAfter() - 1)
            p = END_POS;

        _posTemp = p;

        return x;
    }

    private Xobj getDenormal ( )
    {
        assert isPositioned();
        assert END_POS == -1;
        assert !_xobj.isRoot() || _pos >= END_POS;
        
        Xobj x = _xobj;
        int  p = _pos;

        if (p == 0)
        {
            if (x._prevSibling != null)
            {
                x = x._prevSibling;
                p = x.posMax();
            }
            else
            {
                x = x.ensureParent();
                p = END_POS;
            }
        }
        else if (p == END_POS)
        {
            if (x._lastChild != null)
            {
                x = x._lastChild;
                p = x.posMax();
            }
            else
                p = x.posAfter() - 1;
        }

        _posTemp = p;

        return x;
    }

//    boolean isInvalid ( )
//    {
//        assert isNormal() && isPositioned() && _pos == 0;
//
//        return _xobj.isInvalid();
//    }

    void setType ( SchemaType type )
    {
        assert isNormal() && isPositioned() && _pos == 0;
        _xobj.setType( type );
    }
    
//    TypeStoreUser getTypeStoreUser ( )
//    {
//        assert isNormal() && isPositioned() && _pos == 0;
//
//        return _xobj.getTypeStoreUser();
//    }
    
//    void setTypeStoreUser ( TypeStoreUser user )
//    {
//        assert isNormal() && isPositioned() && _pos == 0;
//
//        _xobj.setTypeStoreUser( user );
//    }

//    TypeStore getTypeStore ( )
//    {
//        assert isNormal() && isPositioned();
//        assert isElem() || isAttr() || isRoot();
//
//        return (TypeStore) _xobj;
//    }
    
    Dom getDom ( )
    {
        assert isNormal();
        assert isPositioned() && kind() != TEXT;

        return _xobj.getDom();
    }

    static void release ( Cur c )
    {
        if (c != null)
            c.release();
    }

    void release ( )
    {
        assert _state != POOLED || _nextTemp == null;

        if (_state == POOLED || _state == DISPOSED)
            return;

        moveToCur( null );

        assert isNormal();

        assert _xobj == null;
        assert _pos  == NO_POS;

        if (_obj instanceof Locale.Ref)
            ((Locale.Ref) _obj).clear();

        _obj = null;

        _curKind = -1;

        assert _state == UNEMBEDDED;

        _locale._unembedded = listRemove( _locale._unembedded );

        if (_locale._curPoolCount < 16)
        {
            _locale._curPool = listInsert( _locale._curPool );
            _state = POOLED;
            _locale._curPoolCount++;
        }
        else
        {
            _locale = null;
            _state = DISPOSED;
        }

        if (_stack != null)
        {
            for ( int i = 0 ; i < _stack.size() ; i++ )
                ((Cur) _stack.get( i )).release();

            _stack.clear();
        }

        clearSelection();
    }

    boolean isOnList ( Cur head )
    {
        for ( ; head != null ; head = head._next )
            if (head == this)
                return true;

        return false;
    }

    Cur listInsert ( Cur head )
    {
        assert _next == null && _prev == null;

        if (head == null)
            head = _prev = this;
        else
        {
            _prev = head._prev;
            head._prev = head._prev._next = this;
        }

        return head;
    }

    Cur listRemove ( Cur head )
    {
        assert _prev != null && isOnList( head );

        if (_prev == this)
            head = null;
        else
        {
            if (head == this)
                head = _next;
            else
                _prev._next = _next;

            if (_next == null)
                head._prev = _prev;
            else
            {
                _next._prev = _prev;
                _next = null;
            }
        }

        _prev = null;
        _state = -1;

        return head;
    }

    private boolean isNormal ( )
    {
        if (_state == POOLED)
            return false;

        if (_state == DISPOSED)
            return _xobj == null && _pos == NO_POS;

        if (_xobj == null)
            return _state == UNEMBEDDED && _pos == NO_POS;

        return _xobj.isNormal( _pos );
    }

    static final class CurLoadContext extends LoadContext
    {
        CurLoadContext ( Locale l )
        {
            _locale = l;
            _frontier = createDomDocumentRootXobj( l );
            _after = false;
            
            _locale._versionAll++;
            _locale._versionSansText++;
        }

        protected void start ( Xobj xo )
        {
            assert _frontier != null;
            assert !_after || _frontier._parent != null;

            if (_after)
            {
                _frontier = _frontier._parent;
                _after = false;
            }

            _frontier.appendXobj( xo );
            _frontier = xo;
        }
        
        protected void end ( )
        {
            assert _frontier != null;
            assert !_after || _frontier._parent != null;

            if (_after)
                _frontier = _frontier._parent;
            else
                _after = true;
        }
        
        protected void startElement ( QName name )
        {
            start(
                createElementXobj( _locale, name, (_after ? _frontier._parent :_frontier)._name ) );
        }
        
        protected void endElement ( )
        {
            assert (_after ? _frontier._parent : _frontier).isElem();
            end();
        }
        
        protected void xmlns ( String prefix, String uri )
        {
            assert prefix == null || prefix.length() > 0;
            assert (_after ? _frontier._parent : _frontier).isContainer();
            
            QName name =
                prefix == null
                    ? _locale.makeQName( null, "xmlns", null )
                    : _locale.makeQName( null, prefix, "xmlns" );
                         
            start( new AttrXobj( _locale, name ) );
            
            text( uri );
            
            end();
        }
        
        protected void attr ( String local, String uri, String prefix, String value )
        {
            assert (_after ? _frontier._parent : _frontier).isContainer();
            start( new AttrXobj( _locale, _locale.makeQName( uri, local, prefix ) ) );
            text( value );
            end();
        }
        
        protected void procInst ( String target, String value )
        {
            start( new ProcInstXobj( _locale, target ) );
            text( value );
            end();
        }
        
        protected void comment ( char[] buf, int off, int cch )
        {
            start( createCommentXobj( _locale ) );
            text( buf, off, cch );
            end();
        }
        
        protected void text ( String s )
        {
            text( s, 0, s.length() );
        }
        
        protected void text ( Object src, int off, int cch )
        {
            if (cch <= 0)
                return;

            if (_after)
            {
                _frontier._srcAfter =
                    _locale._charUtil.saveChars(
                        src, off, cch,
                        _frontier._srcAfter, _frontier._offAfter, _frontier._cchAfter );

                _frontier._offAfter = _locale._charUtil._offSrc;
                _frontier._cchAfter = _locale._charUtil._cchSrc;
            }
            else
            {
                _frontier._srcValue =
                    _locale._charUtil.saveChars(
                        src, off, cch,
                        _frontier._srcValue, _frontier._offValue, _frontier._cchValue );

                _frontier._offValue = _locale._charUtil._offSrc;
                _frontier._cchValue = _locale._charUtil._cchSrc;
            }
        }
        
        protected void text ( char[] src, int off, int cch )
        {
            text( (Object) src, off, cch );
        }
        
        protected Cur finish ( )
        {
            if (_after)
                _frontier = _frontier._parent;

            assert _frontier != null && _frontier._parent == null;

            Cur c = _locale.tempCur();

            c.moveTo( _frontier );

            return c;
        }

        public void dump ( )
        {
            _frontier.dump();
        }
        
        private Locale  _locale;
        private Xobj    _frontier;
        private boolean _after;
    }

    //
    //
    //

    private abstract static class Xobj implements TypeStore
    {
        Xobj ( Locale l, int kind, int domType )
        {
            assert
                kind == ROOT || kind == ELEM || kind == ATTR ||
                    kind == COMMENT || kind == PROCINST;
                    
            _locale = l;
            _bits = (domType << 4) + kind;
        }

        final int kind    ( ) { return _bits & 0xF; }
        final int domType ( ) { return (_bits & 0xF0) >> 4; }
        
        final boolean isRoot      ( ) { return kind() == ROOT; }
        final boolean isAttr      ( ) { return kind() == ATTR; }
        final boolean isElem      ( ) { return kind() == ELEM; }
        final boolean isProcinst  ( ) { return kind() == PROCINST; }
        final boolean isContainer ( ) { return kindIsContainer( kind() ); }
        
        boolean isNormalAttr ( ) { return isAttr() && !isXmlns(); }

        final int cchValue ( ) { return _cchValue; }
        final int cchAfter ( ) { return _cchAfter; }

        final int posAfter ( ) { return 2 + _cchValue; }
        final int posMax   ( ) { return 2 + _cchValue + _cchAfter; }

        boolean isXmlns ( )
        {
            return isAttr() ? Locale.isXmlns( _name ) : false;
        }

        String getXmlnsPrefix ( )
        {
            return Locale.xmlnsPrefix( _name );
        }

        String getXmlnsUri ( )
        {
            return getString( 1, _cchValue );
        }

        boolean hasChildren ( )
        {
            for ( Xobj x = _firstChild ; x != null ; x = x._nextSibling )
                if (!x.isAttr())
                    return true;

            return false;
        }

        boolean hasText ( )
        {
            return _cchValue > 0;
        }

        abstract Dom getDom ( );
        
        abstract Xobj newNode ( );

        //
        // 
        //

//        final boolean isValid    ( ) { return (_bits & 0x100) == 0; }
//        final boolean isInvalid  ( ) { return (_bits & 0x100) != 0; }
//        final void    setValid   ( ) { _bits &= ~0x100; }
//        final void    setInvalid ( ) { _bits &=  0x100; }

        final boolean isVacant    ( ) { return (_bits & 0x100) != 0; }
        final boolean isOccupied  ( ) { return (_bits & 0x100) == 0; }
        final void    setOccupied ( ) { _bits &=  0x100;             }
        final void    setVacant   ( ) { _bits |= ~0x100;             }

//        final void fillVacancy ( )
//        {
//            assert isOccupied();
//
//            throw new RuntimeException( "Not implemented" );
//        }

        final void vacate ( )
        {
            assert getTypeStoreUser() != null;
            
            if (isOccupied())
            {
                setVacant();
                
                throw new RuntimeException( "Not impl" );
//
//                if (hasText() || hasChildren())
//
//                    ....
            }
        }

        void setType ( SchemaType type )
        {
            TypeStoreUser user = getTypeStoreUser();

            if (user == null || user.get_schema_type() == type)
            {
                if (isRoot())
                {
                    disconnectTree();
                    setTypeStoreUserLocal( ((TypeStoreUserFactory) type).createTypeStoreUser() );
                }
                else
                    throw new RuntimeException( "Not impl" );
            }
        }

        private void disconnectTree ( )
        {
            // Disconnect all type store uses in this tree.  If there is no
            // user at the top, then there can be no children.

            // TODO - make not recursive
            if (getTypeStoreUser() != null)
            {
                setTypeStoreUserLocal( null );

                for ( Xobj x = _firstChild ; x != null ; x = x._nextSibling )
                    x.disconnectTree();
            }
        }
    
        final TypeStoreUser getTypeStoreUser ( )
        {
            // Don't assert isNormal() here ... infinite recursion
            return _user;
        }
        
//        private final void setTypeStoreUserHelper ( TypeStoreUser user )
//        {
//            assert isValid();
//            _user = user;
//        }

        // Just set the local user without dealing with disconnecting
        // children...
        
        void setTypeStoreUserLocal ( TypeStoreUser newUser )
        {
            if (isVacant())
            {
                assert _cchValue == 0 && getTypeStoreUser() != null;

                TypeStoreUser oldUser = getTypeStoreUser();

                String newValue = oldUser.build_text( this );

                setOccupied();

                oldUser.disconnect_store();

                Cur c = tempCur();
                c.next();
                c.insertChars( newValue, 0, newValue.length() );
                c.release();
            }

            _user = newUser;
            
            if (newUser != null)
                newUser.attach_store( this );
        }
        
        //
        // Dom interface
        //

        public final Locale locale   ( ) { return _locale;   }
        public final int    nodeType ( ) { return domType(); }
        public final QName  getQName ( ) { return _name;     }
        
        public final Cur tempCur ( )
        {
            Cur c = _locale.tempCur();
            c.moveTo( this );
            return c;
        }

        public void dump ( PrintStream o, Object ref ) { Cur.dump( o, (Xobj) this, ref ); }
        public void dump ( PrintStream o ) { Cur.dump( o, this, this ); }
        public void dump ( ) { dump( System.out ); }

        //
        //
        //

        // TODO - should be able to have Cur's which are not pointing
        // at text not be on either list.
        Cur getEmbedded ( )
        {
            Cur c = _locale._unembedded;

            while ( c != null )
            {
                Cur next = c._next;

                if (c.isPositioned())
                {
                    _locale._unembedded = c.listRemove( _locale._unembedded );
                    c._xobj._embedded = c.listInsert( c._xobj._embedded );
                    c._state = EMBEDDED;
                }

                c = next;
            }
            
            return _embedded;
        }

        //
        //
        //

        final Xobj ensureParent ( )
        {
            assert _parent != null || (!isRoot() && cchAfter() == 0);
            return _parent == null ? new DocumentFragXobj( _locale ).appendXobj( this ) : _parent;
        }

        final int cchRight ( int p )
        {
            assert isNormal( p );
            if (p <= 0) return 0;
            int pa = posAfter();
            return p < pa ? pa - p : posMax() - p;
        }

        final boolean isNormal ( int p )
        {
            if (p == END_POS)
                return true;

            if (p < 0 || p > posMax())
                return false;

            if (isRoot())
            {
                if (p >= posAfter())
                    return false;
            }
            else if (!isAttr())
            {
                if (p >= posMax())
                    return false;
            }
            else if (p >= posAfter())
            {
                if (_cchAfter == 0)
                    return false;

                if (_nextSibling != null && _nextSibling.isAttr())
                    return false;

                if (_parent == null || !(_parent.isRoot() || _parent.kind() == ELEM))
                    return false;
            }

            if (isVacant() && (_cchAfter != 0 || getTypeStoreUser() == null))
                return false;

            return true;
        }

        Xobj removeXobj ( )
        {
            if (_parent != null)
            {
                if (_parent._firstChild == this)
                    _parent._firstChild = _nextSibling;

                if (_parent._lastChild == this)
                    _parent._lastChild = _prevSibling;

                if (_prevSibling != null)
                    _prevSibling._nextSibling = _nextSibling;

                if (_nextSibling != null)
                    _nextSibling._prevSibling = _prevSibling;

                _parent = null;
                _prevSibling = null;
                _nextSibling = null;
            }

            return this;
        }

        Xobj appendXobj ( Xobj c )
        {
            assert _locale == c._locale;
            assert !c.isRoot();
            assert c._parent == null;
            assert c._prevSibling == null;
            assert c._nextSibling == null;
            assert _lastChild == null || _firstChild != null;

            c._parent = this;
            c._prevSibling = _lastChild;

            if (_lastChild == null)
                _firstChild = c;
            else
                _lastChild._nextSibling = c;

            _lastChild = c;

            return this;
        }

        Xobj insertXobj ( Xobj s )
        {
            assert _locale == s._locale;
            assert !s.isRoot() && !isRoot();
            assert s._parent == null;
            assert s._prevSibling == null;
            assert s._nextSibling == null;

            s._parent = _parent;
            s._prevSibling = _prevSibling;
            s._nextSibling = this;

            if (_prevSibling != null)
                _prevSibling._nextSibling = s;
            else
                _parent._firstChild = s;

            _prevSibling = s;

            return this;
        }

        String getString ( int pos, int cch )
        {
            int cchRight = cchRight( pos );

            if (cch < 0 || cch > cchRight)
                cch = cchRight;

            int pa = posAfter();

            // TODO - save this string back into the xobj for use later
            // TODO - save this string back into the xobj for use later
            
            if (pos >= pa)
                return CharUtil.getString( _srcAfter, _offAfter + pos - pa, cch );
            else
                return CharUtil.getString( _srcValue, _offValue + pos - 1, cch );
        }

        Object getChars ( int pos, int cch, Cur c )
        {
            int cchRight = cchRight( pos );

            if (cch < 0 || cch > cchRight)
                cch = cchRight;

            if (cch == 0)
            {
                c._offSrc = 0;
                c._cchSrc = 0;
                
                return null;
            }

            int pa = posAfter();

            Object src;

            if (pos >= pa)
            {
                src = _srcAfter;
                c._offSrc = _offAfter + pos - pa;
            }
            else
            {
                src = _srcValue;
                c._offSrc = _offValue + pos - 1;
            }
            
            c._cchSrc = cch;
            
            return src;
        }

        //
        // TypeStore
        //
        
        public boolean is_attribute ( ) { return isAttr(); }
        public boolean validate_on_set ( ) { return _locale._validateOnSet; }
        public void invalidate_text ( ) { vacate(); }
        
        public XmlCursor new_cursor ( ) { return TypeImpl.typeStore_new_cursor( this ); }
        public void validate ( ValidatorListener vEventSink ) { TypeImpl.typeStore_validate( this, vEventSink ); }
        public SchemaTypeLoader get_schematypeloader ( ) { return TypeImpl.typeStore_get_schematypeloader( this ); }
        public TypeStoreUser change_type ( SchemaType sType ) { return TypeImpl.typeStore_change_type( this, sType ); }
        public QName get_xsi_type ( ) { return TypeImpl.typeStore_get_xsi_type( this ); }
        public String fetch_text ( int whitespaceRule ) { return TypeImpl.typeStore_fetch_text( this, whitespaceRule ); }
        public void store_text ( String text ) { TypeImpl.typeStore_store_text( this, text ); }
        public String compute_default_text ( ) { return TypeImpl.typeStore_compute_default_text( this ); }
        public int compute_flags ( ) { return TypeImpl.typeStore_compute_flags( this ); }
        public SchemaField get_schema_field ( ) { return TypeImpl.typeStore_get_schema_field( this ); }
        public void invalidate_nil ( ) { TypeImpl.typeStore_invalidate_nil( this ); }
        public boolean find_nil ( ) { return TypeImpl.typeStore_find_nil( this ); }
        public int count_elements ( QName name ) { return TypeImpl.typeStore_count_elements( this, name ); }
        public int count_elements ( QNameSet names ) { return TypeImpl.typeStore_count_elements( this, names ); }
        public TypeStoreUser find_element_user ( QName name, int i ) { return TypeImpl.typeStore_find_element_user( this, name, i ); }
        public TypeStoreUser find_element_user ( QNameSet names, int i ) { return TypeImpl.typeStore_find_element_user( this, names, i ); }
        public void find_all_element_users ( QName name, List fillMeUp ) { TypeImpl.typeStore_find_all_element_users( this, name, fillMeUp ); }
        public void find_all_element_users ( QNameSet name, List fillMeUp ) { TypeImpl.typeStore_find_all_element_users( this, name, fillMeUp ); }
        public TypeStoreUser insert_element_user ( QName name, int i ) { return TypeImpl.typeStore_insert_element_user( this, name, i ); }
        public TypeStoreUser insert_element_user ( QNameSet set, QName name, int i ) { return TypeImpl.typeStore_insert_element_user( this, set, name, i ); }
        public TypeStoreUser add_element_user ( QName name ) { return TypeImpl.typeStore_add_element_user( this, name ); }
        public void remove_element ( QName name, int i ) { TypeImpl.typeStore_remove_element( this, name, i ); }
        public void remove_element ( QNameSet names, int i ) { TypeImpl.typeStore_remove_element( this, names, i ); }
        public TypeStoreUser find_attribute_user ( QName name ) { return TypeImpl.typeStore_find_attribute_user( this, name ); }
        public TypeStoreUser add_attribute_user ( QName name ) { return TypeImpl.typeStore_add_attribute_user( this, name ); }
        public void remove_attribute ( QName name ) { TypeImpl.typeStore_remove_attribute( this, name ); }
        public TypeStoreUser copy_contents_from ( TypeStore source ) { return TypeImpl.typeStore_copy_contents_from( this, source ); }
        public void array_setter ( XmlObject[] sources, QName elementName ) { TypeImpl.typeStore_array_setter( this, sources, elementName ); }
        public void visit_elements ( TypeStoreVisitor visitor ) { TypeImpl.typeStore_visit_elements( this, visitor ); }
        public XmlObject[] exec_query ( String queryExpr, XmlOptions options ) throws XmlException { return TypeImpl.typeStore_exec_query( this, queryExpr, options ); }
        public Object get_root_object ( ) { return TypeImpl.typeStore_get_root_object( this ); }
        public String find_prefix_for_nsuri ( String nsuri, String suggested_prefix ) { return TypeImpl.typeStore_find_prefix_for_nsuri( this, nsuri, suggested_prefix ); }
        public String getNamespaceForPrefix ( String prefix ) { return TypeImpl.typeStore_getNamespaceForPrefix( this, prefix ); }
        
        //
        //
        //

        Locale _locale;
        QName _name;

        private Cur _embedded;
        
        private int _bits;

        private Xobj _parent;
        private Xobj _nextSibling;
        private Xobj _prevSibling;
        private Xobj _firstChild;
        private Xobj _lastChild;
        
        private Object _srcValue, _srcAfter;
        private int    _offValue, _offAfter;
        private int    _cchValue, _cchAfter;

        // TODO - put this in a ptr off this node
        private CharNode _charNodesValue;
        private CharNode _charNodesAfter;

        // TODO - put this in a ptr off this node
        private TypeStoreUser _user;
    }

    private abstract static class NodeXobj extends Xobj implements Dom, Node, NodeList
    {
        NodeXobj ( Locale l, int kind, int domType )
        {
            super( l, kind, domType );
        }

        Dom getDom ( ) { return this; }

        //
        //
        //
        
        public int getLength ( ) { return DomImpl._childNodes_getLength( this ); }
        public Node item ( int i ) { return DomImpl._childNodes_item( this, i ); }

        public Node appendChild ( Node newChild ) { return DomImpl._node_appendChild( this, newChild ); }
        public Node cloneNode ( boolean deep ) { return DomImpl._node_cloneNode( this, deep ); }
        public NamedNodeMap getAttributes ( ) { return null; }
        public NodeList getChildNodes ( ) { return this; }
        public Node getParentNode ( ) { return DomImpl._node_getParentNode( this ); }
        public Node removeChild ( Node oldChild ) { return DomImpl._node_removeChild( this, oldChild ); }
        public Node getFirstChild ( ) { return DomImpl._node_getFirstChild( this ); }
        public Node getLastChild ( ) { return DomImpl._node_getLastChild( this ); }
        public String getLocalName ( ) { return DomImpl._node_getLocalName( this ); }
        public String getNamespaceURI ( ) { return DomImpl._node_getNamespaceURI( this ); }
        public Node getNextSibling ( ) { return DomImpl._node_getNextSibling( this ); }
        public String getNodeName ( ) { return DomImpl._node_getNodeName( this ); }
        public short getNodeType ( ) { return DomImpl._node_getNodeType( this ); }
        public String getNodeValue ( ) { return DomImpl._node_getNodeValue( this ); }
        public Document getOwnerDocument ( ) { return DomImpl._node_getOwnerDocument( this ); }
        public String getPrefix ( ) { return DomImpl._node_getPrefix( this ); }
        public Node getPreviousSibling ( ) { return DomImpl._node_getPreviousSibling( this ); }
        public boolean hasAttributes ( ) { return DomImpl._node_hasAttributes( this ); }
        public boolean hasChildNodes ( ) { return DomImpl._node_hasChildNodes( this ); }
        public Node insertBefore ( Node newChild, Node refChild ) { return DomImpl._node_insertBefore( this, newChild, refChild ); }
        public boolean isSupported ( String feature, String version ) { return DomImpl._node_isSupported( this, feature, version ); }
        public void normalize ( ) { DomImpl._node_normalize( this ); }
        public Node replaceChild ( Node newChild, Node oldChild ) { return DomImpl._node_replaceChild( this, newChild, oldChild ); }
        public void setNodeValue ( String nodeValue ) { DomImpl._node_setNodeValue( this, nodeValue ); }
        public void setPrefix ( String prefix ) { DomImpl._node_setPrefix( this, prefix ); }
        
        // DOM Level 3
        public Object getUserData ( String key ) { return DomImpl._node_getUserData( this, key ); }
        public Object setUserData ( String key, Object data, UserDataHandler handler ) { return DomImpl._node_setUserData( this, key, data, handler ); }
        public Object getFeature ( String feature, String version ) { return DomImpl._node_getFeature( this, feature, version ); }
        public boolean isEqualNode ( Node arg ) { return DomImpl._node_isEqualNode( this, arg ); }
        public boolean isSameNode ( Node arg ) { return DomImpl._node_isSameNode( this, arg ); }
        public String lookupNamespaceURI ( String prefix ) { return DomImpl._node_lookupNamespaceURI( this, prefix ); }
        public String lookupPrefix ( String namespaceURI ) { return DomImpl._node_lookupPrefix( this, namespaceURI ); }
        public boolean isDefaultNamespace ( String namespaceURI ) { return DomImpl._node_isDefaultNamespace( this, namespaceURI ); }
        public void setTextContent ( String textContent ) { DomImpl._node_setTextContent( this, textContent ); }
        public String getTextContent ( ) { return DomImpl._node_getTextContent( this ); }
        public short compareDocumentPosition ( Node other ) { return DomImpl._node_compareDocumentPosition( this, other ); }
        public String getBaseURI ( ) { return DomImpl._node_getBaseURI( this ); }
    }

    private final static class DocumentXobj extends NodeXobj implements Document
    {
        DocumentXobj ( Locale l )
        {
            super( l, ROOT, DomImpl.DOCUMENT );
        }
        
        Xobj newNode ( ) { return new DocumentXobj( _locale ); }
        
        //
        //
        //
        
        public Attr createAttribute ( String name ) { return DomImpl._document_createAttribute( this, name ); }
        public Attr createAttributeNS ( String namespaceURI, String qualifiedName ) { return DomImpl._document_createAttributeNS( this, namespaceURI, qualifiedName ); }
        public CDATASection createCDATASection ( String data ) { return DomImpl._document_createCDATASection( this, data ); }
        public Comment createComment ( String data ) { return DomImpl._document_createComment( this, data ); }
        public DocumentFragment createDocumentFragment ( ) { return DomImpl._document_createDocumentFragment( this ); }
        public Element createElement ( String tagName ) { return DomImpl._document_createElement( this, tagName ); }
        public Element createElementNS ( String namespaceURI, String qualifiedName ) { return DomImpl._document_createElementNS( this, namespaceURI, qualifiedName ); }
        public EntityReference createEntityReference ( String name ) { return DomImpl._document_createEntityReference( this, name ); }
        public ProcessingInstruction createProcessingInstruction ( String target, String data ) { return DomImpl._document_createProcessingInstruction( this, target, data ); }
        public Text createTextNode ( String data ) { return DomImpl._document_createTextNode( this, data ); }
        public DocumentType getDoctype ( ) { return DomImpl._document_getDoctype( this ); }
        public Element getDocumentElement ( ) { return DomImpl._document_getDocumentElement( this ); }
        public Element getElementById ( String elementId ) { return DomImpl._document_getElementById( this, elementId ); }
        public NodeList getElementsByTagName ( String tagname ) { return DomImpl._document_getElementsByTagName( this, tagname ); }
        public NodeList getElementsByTagNameNS ( String namespaceURI, String localName ) { return DomImpl._document_getElementsByTagNameNS( this, namespaceURI, localName ); }
        public DOMImplementation getImplementation ( ) { return DomImpl._document_getImplementation( this ); }
        public Node importNode ( Node importedNode, boolean deep ) { return DomImpl._document_importNode( this, importedNode, deep ); }

        // DOM Level 3
        public Node adoptNode ( Node source ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getDocumentURI ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public DOMConfiguration getDomConfig ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getInputEncoding ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public boolean getStrictErrorChecking ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getXmlEncoding ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public boolean getXmlStandalone ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getXmlVersion ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void normalizeDocument ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public Node renameNode ( Node n, String namespaceURI, String qualifiedName ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setDocumentURI ( String documentURI ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setStrictErrorChecking ( boolean strictErrorChecking ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setXmlStandalone ( boolean xmlStandalone ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setXmlVersion ( String xmlVersion ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
    }

    private static class DocumentFragXobj extends NodeXobj implements DocumentFragment
    {
        DocumentFragXobj ( Locale l ) { super( l, ROOT, DomImpl.DOCFRAG ); }
        
        Xobj newNode ( ) { return new DocumentFragXobj( _locale ); }
    }

    private final static class ElementAttributes implements NamedNodeMap
    {
        ElementAttributes ( ElementXobj elementXobj )
        {
            _elementXobj = elementXobj;
        }
        
        public int getLength ( ) { return DomImpl._attributes_getLength( _elementXobj ); }
        public Node getNamedItem ( String name ) { return DomImpl._attributes_getNamedItem ( _elementXobj, name ); }
        public Node getNamedItemNS ( String namespaceURI, String localName ) { return DomImpl._attributes_getNamedItemNS ( _elementXobj, namespaceURI, localName ); }
        public Node item ( int index ) { return DomImpl._attributes_item ( _elementXobj, index ); }
        public Node removeNamedItem ( String name ) { return DomImpl._attributes_removeNamedItem ( _elementXobj, name ); }
        public Node removeNamedItemNS ( String namespaceURI, String localName ) { return DomImpl._attributes_removeNamedItemNS ( _elementXobj, namespaceURI, localName ); }
        public Node setNamedItem ( Node arg ) { return DomImpl._attributes_setNamedItem ( _elementXobj, arg ); }
        public Node setNamedItemNS ( Node arg ) { return DomImpl._attributes_setNamedItemNS ( _elementXobj, arg ); }

        private ElementXobj _elementXobj;
    }
    
    private static class ElementXobj extends NodeXobj implements Element
    {
        ElementXobj ( Locale l, QName name )
        {
            super( l, ELEM, DomImpl.ELEMENT );
            _name = name;
        }
        
        Xobj newNode ( ) { return new ElementXobj( _locale, _name ); }
        
        //
        //
        //
        
        public NamedNodeMap getAttributes ( )
        {
            if (_attributes == null)
                _attributes = new ElementAttributes( this );
            
            return _attributes;
        }
        
        public String getAttribute ( String name ) { return DomImpl._element_getAttribute( this, name ); }
        public Attr getAttributeNode ( String name ) { return DomImpl._element_getAttributeNode( this, name ); }
        public Attr getAttributeNodeNS ( String namespaceURI, String localName ) { return DomImpl._element_getAttributeNodeNS( this, namespaceURI, localName ); }
        public String getAttributeNS ( String namespaceURI, String localName ) { return DomImpl._element_getAttributeNS( this, namespaceURI, localName ); }
        public NodeList getElementsByTagName ( String name ) { return DomImpl._element_getElementsByTagName( this, name ); }
        public NodeList getElementsByTagNameNS ( String namespaceURI, String localName ) { return DomImpl._element_getElementsByTagNameNS( this, namespaceURI, localName ); }
        public String getTagName ( ) { return DomImpl._element_getTagName( this ); }
        public boolean hasAttribute ( String name ) { return DomImpl._element_hasAttribute( this, name ); }
        public boolean hasAttributeNS ( String namespaceURI, String localName ) { return DomImpl._element_hasAttributeNS( this, namespaceURI, localName ); }
        public void removeAttribute ( String name ) { DomImpl._element_removeAttribute( this, name ); }
        public Attr removeAttributeNode ( Attr oldAttr ) { return DomImpl._element_removeAttributeNode( this, oldAttr ); }
        public void removeAttributeNS ( String namespaceURI, String localName ) { DomImpl._element_removeAttributeNS( this, namespaceURI, localName ); }
        public void setAttribute ( String name, String value ) { DomImpl._element_setAttribute( this, name, value ); }
        public Attr setAttributeNode ( Attr newAttr ) { return DomImpl._element_setAttributeNode( this, newAttr ); }
        public Attr setAttributeNodeNS ( Attr newAttr ) { return DomImpl._element_setAttributeNodeNS( this, newAttr ); }
        public void setAttributeNS ( String namespaceURI, String qualifiedName, String value ) { DomImpl._element_setAttributeNS( this, namespaceURI, qualifiedName, value ); }
        
        // DOM Level 3
        public TypeInfo getSchemaTypeInfo ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setIdAttribute ( String name, boolean isId ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setIdAttributeNS ( String namespaceURI, String localName, boolean isId ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setIdAttributeNode ( Attr idAttr, boolean isId ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }

        private ElementAttributes _attributes;
    }

    private static class AttrXobj extends NodeXobj implements Attr
    {
        AttrXobj ( Locale l, QName name )
        {
            super( l, ATTR, DomImpl.ATTR );
            _name = name;
        }

        Xobj newNode ( ) { return new AttrXobj( _locale, _name ); }
        
        //
        //
        //

        public String getName ( ) { return DomImpl._node_getNodeName( this ); }
        public Element getOwnerElement ( ) { return DomImpl._attr_getOwnerElement( this ); }
        public boolean getSpecified ( ) { return DomImpl._attr_getSpecified( this ); }
        public String getValue ( ) { return DomImpl._node_getNodeValue( this ); }
        public void setValue ( String value ) { DomImpl._node_setNodeValue( this, value ); }
        
        // DOM Level 3
        public TypeInfo getSchemaTypeInfo ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public boolean isId ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
    }
    
    private static class CommentXobj extends NodeXobj implements Comment
    {
        CommentXobj ( Locale l ) { super( l, COMMENT, DomImpl.COMMENT ); }

        Xobj newNode ( ) { return new CommentXobj( _locale ); }
        
        public NodeList getChildNodes ( ) { return DomImpl._emptyNodeList; }
        
        public void appendData ( String arg ) { DomImpl._characterData_appendData( this, arg ); }
        public void deleteData ( int offset, int count ) { DomImpl._characterData_deleteData( this, offset, count ); }
        public String getData ( ) { return DomImpl._characterData_getData( this ); }
        public int getLength ( ) { return DomImpl._characterData_getLength( this ); }
        public void insertData ( int offset, String arg ) { DomImpl._characterData_insertData( this, offset, arg ); }
        public void replaceData ( int offset, int count, String arg ) { DomImpl._characterData_replaceData( this, offset, count, arg ); }
        public void setData ( String data ) { DomImpl._characterData_setData( this, data ); }
        public String substringData ( int offset, int count ) { return DomImpl._characterData_substringData( this, offset, count ); }
    }

    private static class ProcInstXobj extends NodeXobj implements ProcessingInstruction
    {
        ProcInstXobj ( Locale l, String target )
        {
            super( l, PROCINST, DomImpl.PROCINST );
            _name = _locale.makeQName( null, target );
        }
        
        Xobj newNode ( ) { return new ProcInstXobj( _locale, _name.getLocalPart() ); }
        
        public String getData ( ) { return DomImpl._processingInstruction_getData( this ); }
        public String getTarget ( ) { return DomImpl._processingInstruction_getTarget( this ); }
        public void setData ( String data ) { DomImpl._processingInstruction_setData( this, data ); }
    }
    
    //
    // SAAJ objects
    //

    private static class SoapPartDocXobj extends Xobj
    {
        SoapPartDocXobj ( Locale l )
        {
            super( l, ROOT, DomImpl.DOCUMENT );
            _soapPartDom = new SoapPartDom( this );
        }

        Dom getDom ( ) { return _soapPartDom; }

        Xobj newNode ( ) { return new SoapPartDocXobj( _locale ); }
        
        SoapPartDom _soapPartDom;
    }
    
    private static class SoapPartDom extends SOAPPart implements Dom, Document, NodeList
    {
        SoapPartDom ( SoapPartDocXobj docXobj )
        {
            _docXobj = docXobj;
        }
        
        public int    nodeType ( ) { return DomImpl.DOCUMENT;   }
        public Locale locale   ( ) { return _docXobj._locale;   }
        public Cur    tempCur  ( ) { return _docXobj.tempCur(); }
        public QName  getQName ( ) { return _docXobj._name;     }
        
        public void dump ( ) { dump( System.out ); }
        public void dump ( PrintStream o ) { _docXobj.dump( o ); }
        public void dump ( PrintStream o, Object ref ) { _docXobj.dump( o, ref ); }

        public String name ( ) { return "#document"; }
        
        public Node appendChild ( Node newChild ) { return DomImpl._node_appendChild( this, newChild ); }
        public Node cloneNode ( boolean deep ) { return DomImpl._node_cloneNode( this, deep ); }
        public NamedNodeMap getAttributes ( ) { return null; }
        public NodeList getChildNodes ( ) { return this; }
        public Node getParentNode ( ) { return DomImpl._node_getParentNode( this ); }
        public Node removeChild ( Node oldChild ) { return DomImpl._node_removeChild( this, oldChild ); }
        public Node getFirstChild ( ) { return DomImpl._node_getFirstChild( this ); }
        public Node getLastChild ( ) { return DomImpl._node_getLastChild( this ); }
        public String getLocalName ( ) { return DomImpl._node_getLocalName( this ); }
        public String getNamespaceURI ( ) { return DomImpl._node_getNamespaceURI( this ); }
        public Node getNextSibling ( ) { return DomImpl._node_getNextSibling( this ); }
        public String getNodeName ( ) { return DomImpl._node_getNodeName( this ); }
        public short getNodeType ( ) { return DomImpl._node_getNodeType( this ); }
        public String getNodeValue ( ) { return DomImpl._node_getNodeValue( this ); }
        public Document getOwnerDocument ( ) { return DomImpl._node_getOwnerDocument( this ); }
        public String getPrefix ( ) { return DomImpl._node_getPrefix( this ); }
        public Node getPreviousSibling ( ) { return DomImpl._node_getPreviousSibling( this ); }
        public boolean hasAttributes ( ) { return DomImpl._node_hasAttributes( this ); }
        public boolean hasChildNodes ( ) { return DomImpl._node_hasChildNodes( this ); }
        public Node insertBefore ( Node newChild, Node refChild ) { return DomImpl._node_insertBefore( this, newChild, refChild ); }
        public boolean isSupported ( String feature, String version ) { return DomImpl._node_isSupported( this, feature, version ); }
        public void normalize ( ) { DomImpl._node_normalize( this ); }
        public Node replaceChild ( Node newChild, Node oldChild ) { return DomImpl._node_replaceChild( this, newChild, oldChild ); }
        public void setNodeValue ( String nodeValue ) { DomImpl._node_setNodeValue( this, nodeValue ); }
        public void setPrefix ( String prefix ) { DomImpl._node_setPrefix( this, prefix ); }
        
        // DOM Level 3
        public Object getUserData ( String key ) { return DomImpl._node_getUserData( this, key ); }
        public Object setUserData ( String key, Object data, UserDataHandler handler ) { return DomImpl._node_setUserData( this, key, data, handler ); }
        public Object getFeature ( String feature, String version ) { return DomImpl._node_getFeature( this, feature, version ); }
        public boolean isEqualNode ( Node arg ) { return DomImpl._node_isEqualNode( this, arg ); }
        public boolean isSameNode ( Node arg ) { return DomImpl._node_isSameNode( this, arg ); }
        public String lookupNamespaceURI ( String prefix ) { return DomImpl._node_lookupNamespaceURI( this, prefix ); }
        public String lookupPrefix ( String namespaceURI ) { return DomImpl._node_lookupPrefix( this, namespaceURI ); }
        public boolean isDefaultNamespace ( String namespaceURI ) { return DomImpl._node_isDefaultNamespace( this, namespaceURI ); }
        public void setTextContent ( String textContent ) { DomImpl._node_setTextContent( this, textContent ); }
        public String getTextContent ( ) { return DomImpl._node_getTextContent( this ); }
        public short compareDocumentPosition ( Node other ) { return DomImpl._node_compareDocumentPosition( this, other ); }
        public String getBaseURI ( ) { return DomImpl._node_getBaseURI( this ); }
        public Node adoptNode ( Node source ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getDocumentURI ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public DOMConfiguration getDomConfig ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getInputEncoding ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public boolean getStrictErrorChecking ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getXmlEncoding ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public boolean getXmlStandalone ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public String getXmlVersion ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void normalizeDocument ( ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public Node renameNode ( Node n, String namespaceURI, String qualifiedName ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setDocumentURI ( String documentURI ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setStrictErrorChecking ( boolean strictErrorChecking ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setXmlStandalone ( boolean xmlStandalone ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
        public void setXmlVersion ( String xmlVersion ) { throw new RuntimeException( "DOM Level 3 Not implemented" ); }
                
        public Attr createAttribute ( String name ) { return DomImpl._document_createAttribute( this, name ); }
        public Attr createAttributeNS ( String namespaceURI, String qualifiedName ) { return DomImpl._document_createAttributeNS( this, namespaceURI, qualifiedName ); }
        public CDATASection createCDATASection ( String data ) { return DomImpl._document_createCDATASection( this, data ); }
        public Comment createComment ( String data ) { return DomImpl._document_createComment( this, data ); }
        public DocumentFragment createDocumentFragment ( ) { return DomImpl._document_createDocumentFragment( this ); }
        public Element createElement ( String tagName ) { return DomImpl._document_createElement( this, tagName ); }
        public Element createElementNS ( String namespaceURI, String qualifiedName ) { return DomImpl._document_createElementNS( this, namespaceURI, qualifiedName ); }
        public EntityReference createEntityReference ( String name ) { return DomImpl._document_createEntityReference( this, name ); }
        public ProcessingInstruction createProcessingInstruction ( String target, String data ) { return DomImpl._document_createProcessingInstruction( this, target, data ); }
        public Text createTextNode ( String data ) { return DomImpl._document_createTextNode( this, data ); }
        public DocumentType getDoctype ( ) { return DomImpl._document_getDoctype( this ); }
        public Element getDocumentElement ( ) { return DomImpl._document_getDocumentElement( this ); }
        public Element getElementById ( String elementId ) { return DomImpl._document_getElementById( this, elementId ); }
        public NodeList getElementsByTagName ( String tagname ) { return DomImpl._document_getElementsByTagName( this, tagname ); }
        public NodeList getElementsByTagNameNS ( String namespaceURI, String localName ) { return DomImpl._document_getElementsByTagNameNS( this, namespaceURI, localName ); }
        public DOMImplementation getImplementation ( ) { return DomImpl._document_getImplementation( this ); }
        public Node importNode ( Node importedNode, boolean deep ) { return DomImpl._document_importNode( this, importedNode, deep ); }

        public int getLength ( ) { return DomImpl._childNodes_getLength( this ); }
        public Node item ( int i ) { return DomImpl._childNodes_item( this, i ); }

        public void removeAllMimeHeaders ( ) { DomImpl._soapPart_removeAllMimeHeaders( this ); }
        public void removeMimeHeader ( String name ) { DomImpl._soapPart_removeMimeHeader( this, name ); }
        public Iterator getAllMimeHeaders ( ) { return DomImpl._soapPart_getAllMimeHeaders( this ); }
        public SOAPEnvelope getEnvelope ( ) { return DomImpl._soapPart_getEnvelope( this ); }
        public Source getContent ( ) { return DomImpl._soapPart_getContent( this ); }
        public void setContent ( Source source ) { DomImpl._soapPart_setContent( this, source ); }
        public String[] getMimeHeader ( String name ) { return DomImpl._soapPart_getMimeHeader( this, name ); }
        public void addMimeHeader ( String name, String value ) { DomImpl._soapPart_addMimeHeader( this, name,value ); }
        public void setMimeHeader ( String name, String value ) { DomImpl._soapPart_setMimeHeader( this, name, value ); }
        public Iterator getMatchingMimeHeaders ( String[] names ) { return DomImpl._soapPart_getMatchingMimeHeaders( this, names ); }
        public Iterator getNonMatchingMimeHeaders ( String[] names ) { return DomImpl._soapPart_getNonMatchingMimeHeaders( this, names ); }
    
        SoapPartDocXobj _docXobj;
    }

    private static class SoapElementXobj
        extends ElementXobj implements SOAPElement, javax.xml.soap.Node
    {
        SoapElementXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapElementXobj( _locale, _name ); }
        
        public void detachNode ( ) { DomImpl._soapNode_detachNode( this ); }
        public void recycleNode ( ) { DomImpl._soapNode_recycleNode( this ); }
        public String getValue ( ) { return DomImpl._soapNode_getValue( this ); }
        public void setValue ( String value ) { DomImpl._soapNode_setValue( this, value ); }
        public SOAPElement getParentElement ( ) { return DomImpl._soapNode_getParentElement( this ); }
        public void setParentElement ( SOAPElement p ) { DomImpl._soapNode_setParentElement( this, p ); }
        
        public void removeContents ( ) { DomImpl._soapElement_removeContents( this ); }
        public String getEncodingStyle ( ) { return DomImpl._soapElement_getEncodingStyle( this ); }
        public void setEncodingStyle ( String encodingStyle ) { DomImpl._soapElement_setEncodingStyle( this, encodingStyle ); }
        public boolean removeNamespaceDeclaration ( String prefix ) { return DomImpl._soapElement_removeNamespaceDeclaration( this, prefix ); }
        public Iterator getAllAttributes ( ) { return DomImpl._soapElement_getAllAttributes( this ); }
        public Iterator getChildElements ( ) { return DomImpl._soapElement_getChildElements( this ); }
        public Iterator getNamespacePrefixes ( ) { return DomImpl._soapElement_getNamespacePrefixes( this ); }
        public SOAPElement addAttribute ( Name name, String value ) throws SOAPException { return DomImpl._soapElement_addAttribute( this, name, value ); }
        public SOAPElement addChildElement ( SOAPElement oldChild ) throws SOAPException { return DomImpl._soapElement_addChildElement( this, oldChild ); }
        public SOAPElement addChildElement ( Name name ) throws SOAPException { return DomImpl._soapElement_addChildElement( this, name ); }
        public SOAPElement addChildElement ( String localName ) throws SOAPException { return DomImpl._soapElement_addChildElement( this, localName ); }
        public SOAPElement addChildElement ( String localName, String prefix ) throws SOAPException { return DomImpl._soapElement_addChildElement( this, localName, prefix ); }
        public SOAPElement addChildElement ( String localName, String prefix, String uri ) throws SOAPException { return DomImpl._soapElement_addChildElement( this, localName, prefix, uri ); }
        public SOAPElement addNamespaceDeclaration ( String prefix, String uri ) { return DomImpl._soapElement_addNamespaceDeclaration( this, prefix, uri ); }
        public SOAPElement addTextNode ( String data ) { return DomImpl._soapElement_addTextNode( this, data ); }
        public String getAttributeValue ( Name name ) { return DomImpl._soapElement_getAttributeValue( this, name ); }
        public Iterator getChildElements ( Name name ) { return DomImpl._soapElement_getChildElements( this, name ); }
        public Name getElementName ( ) { return DomImpl._soapElement_getElementName( this ); }
        public String getNamespaceURI ( String prefix ) { return DomImpl._soapElement_getNamespaceURI( this, prefix ); }
        public Iterator getVisibleNamespacePrefixes ( ) { return DomImpl._soapElement_getVisibleNamespacePrefixes( this ); }
        public boolean removeAttribute ( Name name ) { return DomImpl._soapElement_removeAttribute( this, name ); }
    }
    
    private static class SoapBodyXobj extends SoapElementXobj implements SOAPBody
    {
        SoapBodyXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapBodyXobj( _locale, _name ); }
        
        public boolean hasFault ( ) { return DomImpl.soapBody_hasFault( this ); }
        public SOAPFault addFault ( ) throws SOAPException { return DomImpl.soapBody_addFault( this ); }
        public SOAPFault getFault ( ) { return DomImpl.soapBody_getFault( this ); }
        public SOAPBodyElement addBodyElement ( Name name ) { return DomImpl.soapBody_addBodyElement( this, name ); }
        public SOAPBodyElement addDocument ( Document document ) { return DomImpl.soapBody_addDocument( this, document ); }
        public SOAPFault addFault ( Name name, String s ) throws SOAPException { return DomImpl.soapBody_addFault( this, name, s ); }
        public SOAPFault addFault ( Name faultCode, String faultString, java.util.Locale locale ) throws SOAPException { return DomImpl.soapBody_addFault( this, faultCode, faultString, locale ); }
    }
    
    private static class SoapBodyElementXobj extends SoapElementXobj implements SOAPBodyElement
    {
        SoapBodyElementXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapBodyElementXobj( _locale, _name ); }
    }
    
    private static class SoapEnvelopeXobj extends SoapElementXobj implements SOAPEnvelope
    {
        SoapEnvelopeXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapEnvelopeXobj( _locale, _name ); }
        
        public SOAPBody addBody ( ) throws SOAPException { return DomImpl._soapEnvelope_addBody( this ); }
        public SOAPBody getBody ( ) throws SOAPException { return DomImpl._soapEnvelope_getBody( this ); }
        public SOAPHeader getHeader ( ) throws SOAPException { return DomImpl._soapEnvelope_getHeader( this ); }
        public SOAPHeader addHeader ( ) throws SOAPException { return DomImpl._soapEnvelope_addHeader( this ); }
        public Name createName ( String localName ) { return DomImpl._soapEnvelope_createName( this, localName ); }
        public Name createName ( String localName, String prefix, String namespaceURI ) { return DomImpl._soapEnvelope_createName( this, localName, prefix, namespaceURI ); }
    }

    private static class SoapHeaderXobj extends SoapElementXobj implements SOAPHeader
    {
        SoapHeaderXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapHeaderXobj( _locale, _name ); }
        
        public Iterator examineAllHeaderElements ( ) { return DomImpl.soapHeader_examineAllHeaderElements( this ); }
        public Iterator extractAllHeaderElements ( ) { return DomImpl.soapHeader_extractAllHeaderElements( this ); }
        public Iterator examineHeaderElements ( String actor ) { return DomImpl.soapHeader_examineHeaderElements( this, actor ); }
        public Iterator examineMustUnderstandHeaderElements ( String mustUnderstandString ) { return DomImpl.soapHeader_examineMustUnderstandHeaderElements( this, mustUnderstandString ); }
        public Iterator extractHeaderElements ( String actor ) { return DomImpl.soapHeader_extractHeaderElements( this, actor ); }
        public SOAPHeaderElement addHeaderElement ( Name name ) { return DomImpl.soapHeader_addHeaderElement( this, name ); }
    }
    
    private static class SoapHeaderElementXobj extends SoapElementXobj implements SOAPHeaderElement
    {
        SoapHeaderElementXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapHeaderElementXobj( _locale, _name ); }
        
        public void setMustUnderstand ( boolean mustUnderstand ) { DomImpl.soapHeaderElement_setMustUnderstand( this, mustUnderstand ); }
        public boolean getMustUnderstand ( ) { return DomImpl.soapHeaderElement_getMustUnderstand( this ); }
        public void setActor ( String actor ) { DomImpl.soapHeaderElement_setActor( this, actor ); }
        public String getActor ( ) { return DomImpl.soapHeaderElement_getActor( this ); }
    }
    
    private static class SoapFaultXobj extends SoapBodyElementXobj implements SOAPFault
    {
        SoapFaultXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new SoapFaultXobj( _locale, _name ); }

        public void setFaultString ( String faultString ) { DomImpl.soapFault_setFaultString( this, faultString ); }
        public void setFaultString ( String faultString, java.util.Locale locale ) { DomImpl.soapFault_setFaultString( this, faultString, locale ); }
        public void setFaultCode ( Name faultCodeName ) throws SOAPException { DomImpl.soapFault_setFaultCode( this, faultCodeName ); }
        public void setFaultActor ( String faultActorString ) { DomImpl.soapFault_setFaultActor( this, faultActorString ); }
        public String getFaultActor ( ) { return DomImpl.soapFault_getFaultActor( this ); }
        public String getFaultCode ( ) { return DomImpl.soapFault_getFaultCode( this ); }
        public void setFaultCode ( String faultCode ) throws SOAPException { DomImpl.soapFault_setFaultCode( this, faultCode ); }
        public java.util.Locale getFaultStringLocale ( ) { return DomImpl.soapFault_getFaultStringLocale( this ); }
        public Name getFaultCodeAsName ( ) { return DomImpl.soapFault_getFaultCodeAsName( this ); }
        public String getFaultString ( ) { return DomImpl.soapFault_getFaultString( this ); }
        public Detail addDetail ( ) throws SOAPException { return DomImpl.soapFault_addDetail( this ); }
        public Detail getDetail ( ) { return DomImpl.soapFault_getDetail( this ); }
    }

    private static class SoapFaultElementXobj extends SoapElementXobj implements SOAPFaultElement
    {
        SoapFaultElementXobj ( Locale l, QName name ) { super( l, name ); }

        Xobj newNode ( ) { return new SoapFaultElementXobj( _locale, _name ); }
    }
    
    private static class DetailXobj extends SoapFaultElementXobj implements Detail
    {
        DetailXobj ( Locale l, QName name ) { super( l, name ); }
        
        Xobj newNode ( ) { return new DetailXobj( _locale, _name ); }
        
        public DetailEntry addDetailEntry ( Name name ) { return DomImpl.detail_addDetailEntry( this, name ); }
        public Iterator getDetailEntries ( ) { return DomImpl.detail_getDetailEntries( this ); }
    }

    private static class DetailEntryXobj extends SoapElementXobj implements DetailEntry
    {
        Xobj newNode ( ) { return new DetailEntryXobj( _locale, _name ); }

        DetailEntryXobj ( Locale l, QName name ) { super( l, name ); }
    }

    private static class SaajCommentXobj extends CommentXobj implements javax.xml.soap.Text
    {
        SaajCommentXobj ( Locale l ) { super( l ); }

        Xobj newNode ( ) { return new SaajCommentXobj( _locale ); }
        
        public Text splitText ( int offset ) { throw new IllegalStateException(); }
        public String getWholeText ( ) { throw new IllegalStateException(); }
        public boolean isElementContentWhitespace ( ) { throw new IllegalStateException(); }
        public Text replaceWholeText ( String content ) { throw new IllegalStateException(); }

        public boolean isComment ( ) { return true; }
        
        public void detachNode ( ) { DomImpl._soapNode_detachNode( this ); }
        public void recycleNode ( ) { DomImpl._soapNode_recycleNode( this ); }
        public String getValue ( ) { return DomImpl._soapNode_getValue( this ); }
        public void setValue ( String value ) { DomImpl._soapNode_setValue( this, value ); }
        public SOAPElement getParentElement ( ) { return DomImpl._soapNode_getParentElement( this ); }
        public void setParentElement ( SOAPElement p ) { DomImpl._soapNode_setParentElement( this, p ); }
        
        public void appendData ( String arg ) { DomImpl._characterData_appendData( this, arg ); }
        public void deleteData ( int offset, int count ) { DomImpl._characterData_deleteData( this, offset, count ); }
        public String getData ( ) { return DomImpl._characterData_getData( this ); }
        public int getLength ( ) { return DomImpl._characterData_getLength( this ); }
        public void insertData ( int offset, String arg ) { DomImpl._characterData_insertData( this, offset, arg ); }
        public void replaceData ( int offset, int count, String arg ) { DomImpl._characterData_replaceData( this, offset, count, arg ); }
        public void setData ( String data ) { DomImpl._characterData_setData( this, data ); }
        public String substringData ( int offset, int count ) { return DomImpl._characterData_substringData( this, offset, count ); }
    }

    //
    //
    //

    static String kindName ( int kind )
    {
        switch ( kind )
        {
            case ROOT     : return "ROOT";
            case ELEM     : return "ELEM";
            case ATTR     : return "ATTR";
            case COMMENT  : return "COMMENT";
            case PROCINST : return "PROCINST";
            case TEXT     : return "TEXT";
            default       : return "<< Unknown Kind (" + kind + ") >>";
        }
    }

    static void dump ( PrintStream o, Dom d, Object ref )
    {
    }
    
    static void dump ( PrintStream o, Dom d )
    {
        d.dump( o );
    }

    static void dump ( Dom d )
    {
        dump( System.out, d );
    }

    static void dump ( Node n )
    {
        dump( System.out, n );
    }

    static void dump ( PrintStream o, Node n )
    {
        dump( o, (Dom) n );
    }

    void dump ( )
    {
        dump( System.out, _xobj, this );
    }

    void dump ( PrintStream o )
    {
        if (_xobj == null)
        {
            o.println( "Unpositioned xptr" );
            return;
        }

        dump( o, _xobj, this );
    }

    public static void dump ( PrintStream o, Xobj xo, Object ref )
    {
        if (ref == null)
            ref = xo;
        
        while ( xo._parent != null )
            xo = xo._parent;

        dumpXobj( o, xo, 0, ref );
        
        o.println();
    }
    
    private static void dumpCur ( PrintStream o, String prefix, Cur c, Object ref )
    {
        o.print( " " );
        
        if (ref == c)
            o.print( "*:" );
        
        o.print( prefix + "cur[" + c._pos + "]" );
    }
    
    private static void dumpCurs ( PrintStream o, Xobj xo, Object ref )
    {
        for ( Cur c = xo._embedded ; c != null ; c = c._next )
            dumpCur( o, "E:", c, ref );
        
        for ( Cur c = xo._locale._unembedded ; c != null ; c = c._next )
        {
            if (c._xobj == xo)
                dumpCur( o, "U:", c, ref );
        }
    }
    
    private static void dumpCharNodes ( PrintStream o, CharNode nodes, Object ref )
    {
        for ( CharNode n = nodes ; n != null ; n = n._next )
        {
            o.print( " " );
            
            if (n == ref)
                o.print( "*" );
            
            o.print( (n instanceof TextNode ? "TEXT" : "CDATA") + "[" + n._cch + "]" );
        }
    }
    
    private static void dumpXobj ( PrintStream o, Xobj xo, int level, Object ref )
    {
        if (xo == null)
            return;

        if (xo == ref)
            o.print( "* " );
        else
            o.print( "  " );
        
        for ( int i = 0 ; i < level ; i++ )
            o.print( "  " );

        o.print( kindName( xo.kind() ) );

        if (xo._name != null)
        {
            o.print( " " );
            
            if (xo._name.getPrefix().length() > 0)
                o.print( xo._name.getPrefix() + ":" );
            
            o.print( xo._name.getLocalPart() );

            if (xo._name.getNamespaceURI().length() > 0)
                o.print( "@" + xo._name.getNamespaceURI() );
        }

        if (xo._srcValue != null || xo._charNodesValue != null)
        {
            o.print( " Value( " );
            o.print( "\"" + CharUtil.getString( xo._srcValue, xo._offValue, xo._cchValue ) + "\"" );
//            CharUtil.dumpChars( o, xo._srcValue, xo._offValue, xo._cchValue );
            dumpCharNodes( o, xo._charNodesValue, ref );
            o.print( " )" );
        }

        if (xo._srcAfter != null || xo._charNodesAfter != null)
        {
            o.print( " After( " );
            o.print( "\"" + CharUtil.getString( xo._srcAfter, xo._offAfter, xo._cchAfter ) + "\"" );
//            CharUtil.dumpChars( o, xo._srcAfter, xo._offAfter, xo._cchAfter );
            dumpCharNodes( o, xo._charNodesAfter, ref );
            o.print( " )" );
        }

        dumpCurs( o, xo, ref );

        String className = xo.getClass().getName();
        
        int i = className.lastIndexOf( '.' );
        
        if (i > 0)
        {
            className = className.substring( i + 1 );
            
            i = className.lastIndexOf( '$' );

            if (i > 0)
                className = className.substring( i + 1 );
        }

        o.print( " (" );
        o.print( className );
        o.print( ")" );
        
        o.println();

        for ( xo = xo._firstChild ; xo != null ; xo = xo._nextSibling )
            dumpXobj( o, xo, level + 1, ref );
    }
    
    //
    //
    //

    Locale _locale;
    
    private Xobj _xobj;
    private int _pos;

    int _state;
    int _curKind;

    Cur _nextTemp;
    int _tempFrame;

    Cur _next, _prev;
    
    Object _obj;

    ArrayList _stack;
    ArrayList _selection;
    
    private int _posTemp;
    
    int _offSrc;
    int _cchSrc;
}