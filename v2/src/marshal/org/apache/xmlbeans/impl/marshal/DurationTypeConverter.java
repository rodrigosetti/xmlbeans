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

import org.apache.xmlbeans.GDuration;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.common.InvalidLexicalValueException;


/**
 * convert between schema Duration and GDuration
 */
final class DurationTypeConverter
    extends BaseSimpleTypeConverter
{

    protected Object getObject(UnmarshalResult context) throws XmlException
    {
        return context.getGDurationValue();
    }

    public Object unmarshalAttribute(UnmarshalResult context) throws XmlException
    {
        return context.getAttributeGDurationValue();
    }

    public Object unmarshalAttribute(CharSequence lexical_value,
                                     UnmarshalResult result)
        throws XmlException
    {
        try {
            return new GDuration(lexical_value);
        }
        catch (IllegalArgumentException e) {
            throw new InvalidLexicalValueException(e, result.getLocation());
        }

    }

    public CharSequence print(Object value, MarshalResult result)
    {
        GDuration cal = (GDuration)value;
        return cal.toString();
    }
}