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

package org.apache.xmlbeans.impl.marshal;

import org.apache.xml.xmlbeans.bindingConfig.BindingConfigDocument;
import org.apache.xmlbeans.BindingContext;
import org.apache.xmlbeans.BindingContextFactory;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.binding.bts.BindingFile;
import org.apache.xmlbeans.impl.binding.bts.BindingLoader;
import org.apache.xmlbeans.impl.binding.bts.BuiltinBindingLoader;
import org.apache.xmlbeans.impl.binding.bts.CompositeBindingLoader;
import org.apache.xmlbeans.impl.binding.tylar.DefaultTylarLoader;
import org.apache.xmlbeans.impl.binding.tylar.Tylar;
import org.apache.xmlbeans.impl.binding.tylar.TylarLoader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.jar.JarInputStream;

/**
 * creates BindingContext objects from various inputs.
 */
public final class BindingContextFactoryImpl extends BindingContextFactory
{

    public BindingContext createBindingContext(URI tylarUri)
        throws IOException, XmlException
    {
        return createBindingContext(new URI[]{tylarUri});
    }

    public BindingContext createBindingContext(URI[] tylarUris)
        throws IOException, XmlException
    {
        if (tylarUris == null) throw new IllegalArgumentException("null uris");
        //FIXME loader class needs to be pluggable
        TylarLoader loader = DefaultTylarLoader.getInstance();
        if (loader == null) throw new IllegalStateException("null loader");
        return createBindingContext(loader.load(tylarUris));
    }

    public BindingContext createBindingContext(JarInputStream jar)
        throws IOException, XmlException
    {
        if (jar == null) throw new IllegalArgumentException("null InputStream");
        //FIXME loader class needs to be pluggable
        TylarLoader loader = DefaultTylarLoader.getInstance();
        if (loader == null) throw new IllegalStateException("null TylarLoader");
        return createBindingContext(loader.load(jar));
    }

    // REVIEW It's unfortunate that we can't expose this method to the public
    // at the moment.  It's easy to imagine cases where one has already built
    // up the tylar and doesn't want to pay the cost of re-parsing it.
    // Of course, exposing it means we expose Tylar to the public as well,
    // and this should be done with caution.
    public BindingContext createBindingContext(Tylar tylar)
    {
        // build the loader chain - this is the binding files plus
        // the builtin loader
        BindingLoader loader = tylar.getBindingLoader();
        // finally, glue it all together
        return new BindingContextImpl(loader);
    }

    public BindingContext createBindingContext()
    {
        BindingFile empty = new BindingFile();
        return createBindingContext(empty);
    }

    // ========================================================================
    // Private methods

    private static BindingContextImpl createBindingContext(BindingFile bf)
    {
        BindingLoader bindingLoader = buildBindingLoader(bf);
        return new BindingContextImpl(bindingLoader);
    }

    private static BindingLoader buildBindingLoader(BindingFile bf)
    {
        BindingLoader builtins = BuiltinBindingLoader.getInstance();
        return CompositeBindingLoader.forPath(new BindingLoader[]{builtins, bf});
    }

    /**
     * @deprecated We no longer support naked config files.  This is currently
     * only used by MarshalTests
     */
    public BindingContext createBindingContextFromConfig(File bindingConfig)
        throws IOException, XmlException
    {
        BindingConfigDocument doc =
            BindingConfigDocument.Factory.parse(bindingConfig);
        BindingFile bf = BindingFile.forDoc(doc);
        return createBindingContext(bf);
    }


}