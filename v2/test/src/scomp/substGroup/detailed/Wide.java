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

package scomp.substGroup.detailed;

import scomp.common.BaseCase;
import xbean.scomp.substGroup.wide.*;
import xbean.scomp.substGroup.deep.ItemType;
import xbean.scomp.substGroup.deep.ProductType;
import xbean.scomp.substGroup.deep.ItemsDocument;

import java.math.BigInteger;

/**
 * @owner: ykadiysk
 * Date: Jul 29, 2004
 * Time: 3:31:59 PM
 */
public class Wide extends BaseCase{

      public void testValidSubstParse() throws Throwable {
        String input=
                "<base:items xmlns:pre=\"http://xbean/scomp/substGroup/Wide\"" +
                " xmlns:base=\"http://xbean/scomp/substGroup/Deep\">" +
                "<pre:businessshirt>" +
                " <number>SKU25</number>" +
                " <name>Oxford Shirt</name>" +
                " <size>12</size>" +
                " <color>white</color>" +
                "</pre:businessshirt>" +
                "<base:product>" +
                  " <number>SKU45</number>" +
                "   <name>Accessory</name>" +
                "</base:product>" +
                "<pre:beachumbrella diameter=\"1.43\">" +
                   " <number>SKU15</number>" +
                "   <name>Umbrella</name>" +
                "</pre:beachumbrella>" +
                "</base:items>";
          ItemsDocument doc = ItemsDocument.Factory.parse(input);
         try {
            assertTrue(doc.validate(validateOptions));
        }
        catch (Throwable t) {
            showErrors();
            throw t;
        }

    }
    /**
     * Test error message. 1 product too many
     * @throws Throwable
     */
     public void testValidSubstParseInvalid() throws Throwable {
         String input=
                "<base:items xmlns:pre=\"http://xbean/scomp/substGroup/Wide\"" +
                " xmlns:base=\"http://xbean/scomp/substGroup/Deep\">" +
                  "<pre:businessshirt>" +
                " <number>SKU25</number>" +
                " <name>Oxford Shirt</name>" +
                " <size>12</size>" +
                " <color>blue</color>" +
                "</pre:businessshirt>" +
                "<base:product>" +
                  " <number>SKU45</number>" +
                "   <name>Accessory</name>" +
                "</base:product>" +
                "<pre:beachumbrella diameter=\"1.43\">" +
                   " <number>SKU15</number>" +
                "   <name>Umbrella</name>" +
                "</pre:beachumbrella>" +
                   "<pre:baseballhat TeamName=\"Mariners\">" +
                   " <number>SKU15</number>" +
                "   <name>Umbrella</name>" +
                "</pre:baseballhat>" +
                "</base:items>";
          ItemsDocument doc = ItemsDocument.Factory.parse(input);
         try {
            assertTrue( !doc.validate(validateOptions));
        }
        catch (Throwable t) {
            showErrors();
            throw t;
        }
     }

    public void testValidSubstBuild() throws Throwable {
        ItemsDocument doc = ItemsDocument.Factory.newInstance();
        ItemType items = doc.addNewItems();
        BusinessShirtType bShirt=BusinessShirtType.Factory.newInstance();
        bShirt.setName("Funny Shirt");
        bShirt.setNumber("SKU84");
        bShirt.setSize(new BigInteger("10"));
        bShirt.setColor("blue");

        BeachUmbrellaT  bu=BeachUmbrellaT.Factory.newInstance();
        bu.setName("Beach umbrella");
        bu.setNumber("SKU-BU");
        bu.setDiameter(1.4f);

        ProductType genericProd = ProductType.Factory.newInstance();
        genericProd.setName("Pants");
        genericProd.setNumber("32");
        items.setProductArray(new ProductType[]{bShirt,bu,genericProd});
        //shirt must be white
         assertTrue(!doc.validate());

        //TODO: is the object being copied? Should this change the color?
         bShirt.setColor("white");

        try {
            assertTrue(doc.validate(validateOptions));
        }
        catch (Throwable t) {
            showErrors();
            throw t;
        }
    }


}