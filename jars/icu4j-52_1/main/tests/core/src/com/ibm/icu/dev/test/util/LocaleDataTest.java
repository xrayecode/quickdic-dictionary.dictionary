/*
 *******************************************************************************
 * Copyright (C) 2003-2013, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.dev.test.util;

import java.util.Arrays;
import java.util.HashSet;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.LocaleData;
import com.ibm.icu.util.ULocale;

/**
 * @author ram
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class LocaleDataTest extends TestFmwk{

    public static void main(String[] args) throws Exception{
        new LocaleDataTest().run(args);
    }
    
    private ULocale[] availableLocales = null;
    
    public LocaleDataTest(){
    }
    protected void init(){
        availableLocales = ICUResourceBundle.getAvailableULocales();
    }
    public void TestPaperSize(){
        for(int i = 0; i < availableLocales.length; i++){
            ULocale locale = availableLocales[i];
            LocaleData.PaperSize paperSize = LocaleData.getPaperSize(locale);
            // skip testing of "in" .. deprecated code for Indonesian
            String lang = locale.getLanguage();
            if(lang.equals("in")){
                continue;
            }
            ULocale fullLoc = ULocale.addLikelySubtags(locale);
            if(fullLoc.toString().indexOf("_BZ") >= 0 || fullLoc.toString().indexOf("_CA") >= 0 ||
               fullLoc.toString().indexOf("_CL") >= 0 || fullLoc.toString().indexOf("_CO") >= 0 ||
               fullLoc.toString().indexOf("_CR") >= 0 || fullLoc.toString().indexOf("_GT") >= 0 ||
               fullLoc.toString().indexOf("_MX") >= 0 || fullLoc.toString().indexOf("_NI") >= 0 ||
               fullLoc.toString().indexOf("_PA") >= 0 || fullLoc.toString().indexOf("_PH") >= 0 ||
               fullLoc.toString().indexOf("_PR") >= 0 || fullLoc.toString().indexOf("_SV") >= 0 ||
               fullLoc.toString().indexOf("_US") >= 0 || fullLoc.toString().indexOf("_VE") >= 0 ){
                if(paperSize.getHeight()!= 279 || paperSize.getWidth() != 216 ){
                    errln("PaperSize did not return the expected value for locale "+ locale+
                          " Expected height: 279 width: 216."+
                          " Got height: "+paperSize.getHeight()+" width: "+paperSize.getWidth()
                           );
                }else{
                    logln("PaperSize returned the expected values for locale " + locale);
                }
            }else{
                if(paperSize.getHeight()!= 297 || paperSize.getWidth() != 210 ){
                    errln("PaperSize did not return the expected value for locale "+ locale +
                          " Expected height: 297 width: 210."+
                          " Got height: "+paperSize.getHeight() +" width: "+paperSize.getWidth() 
                           );
                }else{
                    logln("PaperSize returned the expected values for locale " + locale);
                }
            }
        }
    }
    public void TestMeasurementSystem(){
        for(int i=0; i<availableLocales.length; i++){
            ULocale locale = availableLocales[i];
            LocaleData.MeasurementSystem ms = LocaleData.getMeasurementSystem(locale);
            // skip testing of "in" .. deprecated code for Indonesian
            String lang = locale.getLanguage();
            if(lang.equals("in")){
                continue;
            }           
            ULocale fullLoc = ULocale.addLikelySubtags(locale);
            if(fullLoc.toString().indexOf("_US") >= 0 || fullLoc.toString().indexOf("_MM") >= 0 || fullLoc.toString().indexOf("_LR") >= 0){
                if(ms == LocaleData.MeasurementSystem.US){
                    logln("Got the expected measurement system for locale: " + locale);
                }else{
                    errln("Did not get the expected measurement system for locale: "+ locale);
                }
            }else{
                if(ms == LocaleData.MeasurementSystem.SI){
                    logln("Got the expected measurement system for locale: " + locale);
                }else{
                    errln("Did not get the expected measurement system for locale: "+ locale);
                } 
            }
        }
    }

    // Simple test case for checking exemplar character type coverage
    public void TestEnglishExemplarCharacters() {
        final char[] testChars = {
            0x61,   // standard
            0xE1,   // auxiliary
            0x41,   // index
            0,      // filler for deprecated currency exemplar
            0x2D,   // punctuation
        };
        LocaleData ld = LocaleData.getInstance(ULocale.ENGLISH);
        for (int type = 0; type < LocaleData.ES_COUNT; type++) {
            UnicodeSet exSet = ld.getExemplarSet(0, type);
            if (exSet != null) {
                if (testChars[type] > 0 && !exSet.contains(testChars[type])) {
                    errln("Character '" + testChars[type] + "' is not included in exemplar type " + type);
                }
            }
        }
    }

    // Bundle together a UnicodeSet (of expemplars) and ScriptCode combination.
    //   We keep a set of combinations that have already been tested, to
    //   avoid repeated (time consuming) retesting of the same data.
    //   Instances of this class must be well behaved as members of a set.
    static class ExemplarGroup {
        private int[] scs;
        private UnicodeSet set;
        
        ExemplarGroup(UnicodeSet s, int[] scriptCodes) {
            set = s;
            scs = scriptCodes;
        }
        public int hashCode() {
            int hash = 0;
            for (int i=0; i<scs.length && i<4; i++) {
                hash = (hash<<8)+scs[i];
            }
            return hash;
        }        
        public boolean equals(Object other) {
            ExemplarGroup o = (ExemplarGroup)other;
            boolean r = Arrays.equals(scs, o.scs) &&
                         set.equals(o.set);
            return r;
        }
    }
    
    public void TestExemplarSet(){
        HashSet  testedExemplars = new HashSet();
        int equalCount = 0;
        for(int i=0; i<availableLocales.length; i++){
            ULocale locale = availableLocales[i];
            int[] scriptCodes = UScript.getCode(locale);
            if (scriptCodes==null) {
                // I hate the JDK's solution for deprecated language codes.
                // Why does the Locale constructor change the string I passed to it ?
                // such a broken hack !!!!!
                // so in effect I can never test the script code for Indonesian :(
                if(locale.toString().indexOf(("in"))<0){
                    errln("UScript.getCode returned null for locale: " + locale); 
                }
                continue;
            }
            UnicodeSet exemplarSets[] = new UnicodeSet[2];
            for (int k=0; k<2; ++k) {   // for casing option in (normal, caseInsensitive)
                int option = (k==0) ? 0 : UnicodeSet.CASE;
                UnicodeSet exemplarSet = LocaleData.getExemplarSet(locale, option);
                exemplarSets[k] = exemplarSet;
                ExemplarGroup exGrp = new ExemplarGroup(exemplarSet, scriptCodes);
                if (!testedExemplars.contains(exGrp)) {
                    testedExemplars.add(exGrp);
                    UnicodeSet[] sets = new UnicodeSet[scriptCodes.length];
                    // create the UnicodeSets for the script
                    for(int j=0; j < scriptCodes.length; j++){
                        sets[j] = new UnicodeSet("[:" + UScript.getShortName(scriptCodes[j]) + ":]");
                    }
                    boolean existsInScript = false;
                    UnicodeSetIterator iter = new UnicodeSetIterator(exemplarSet);
                    // iterate over the 
                    while (!existsInScript && iter.nextRange()) {
                        if (iter.codepoint != UnicodeSetIterator.IS_STRING) {
                            for(int j=0; j<sets.length; j++){
                                if(sets[j].contains(iter.codepoint, iter.codepointEnd)){
                                    existsInScript = true;
                                    break;
                                }
                            }
                        } else {
                            for(int j=0; j<sets.length; j++){
                                if(sets[j].contains(iter.string)){
                                    existsInScript = true;
                                    break;
                                }
                            }
                        }
                    }
                    if(existsInScript == false){
                        errln("ExemplarSet containment failed for locale : "+ locale);
                    }
                }
            }
            // This is expensive, so only do it if it will be visible
            if (isVerbose()) {
                logln(locale.toString() + " exemplar " + exemplarSets[0]);
                logln(locale.toString() + " exemplar(case-folded) " + exemplarSets[1]);
            }
            assertTrue(locale.toString() + " case-folded is a superset",
                    exemplarSets[1].containsAll(exemplarSets[0]));
            if (exemplarSets[1].equals(exemplarSets[0])) {
                ++equalCount;
            }
        }
        // Note: The case-folded set should sometimes be a strict superset
        // and sometimes be equal.
        assertTrue("case-folded is sometimes a strict superset, and sometimes equal",
                   equalCount > 0 && equalCount < availableLocales.length);
    }
    public void TestExemplarSet2(){
        int equalCount = 0;
        HashSet  testedExemplars = new HashSet();
        for(int i=0; i<availableLocales.length; i++){
            ULocale locale = availableLocales[i];
            LocaleData ld = LocaleData.getInstance(locale);
            int[] scriptCodes = UScript.getCode(locale);
            if (scriptCodes==null) {
                if(locale.toString().indexOf(("in"))<0){
                    errln("UScript.getCode returned null for locale: "+ locale); 
                }
                continue;
            }
            UnicodeSet exemplarSets[] = new UnicodeSet[4];

            for (int k=0; k<2; ++k) {  // for casing option in (normal, uncased)
                int option = (k==0) ? 0 : UnicodeSet.CASE;
                for(int h=0; h<2; ++h){  
                    int type = (h==0) ? LocaleData.ES_STANDARD : LocaleData.ES_AUXILIARY;

                    UnicodeSet exemplarSet = ld.getExemplarSet(option, type);
                    exemplarSets[k*2+h] = exemplarSet;

                    ExemplarGroup exGrp = new ExemplarGroup(exemplarSet, scriptCodes);
                    if (!testedExemplars.contains(exGrp)) {
                        testedExemplars.add(exGrp);
                        UnicodeSet[] sets = new UnicodeSet[scriptCodes.length];
                        // create the UnicodeSets for the script
                        for(int j=0; j < scriptCodes.length; j++){
                            sets[j] = new UnicodeSet("[:" + UScript.getShortName(scriptCodes[j]) + ":]");
                        }
                        boolean existsInScript = false;
                        UnicodeSetIterator iter = new UnicodeSetIterator(exemplarSet);
                        // iterate over the 
                        while (!existsInScript && iter.nextRange()) {
                            if (iter.codepoint != UnicodeSetIterator.IS_STRING) {
                                for(int j=0; j<sets.length; j++){
                                    if(sets[j].contains(iter.codepoint, iter.codepointEnd)){
                                        existsInScript = true;
                                        break;
                                    }
                                }
                            } else {
                                for(int j=0; j<sets.length; j++){
                                    if(sets[j].contains(iter.string)){
                                        existsInScript = true;
                                        break;
                                    }
                                }
                            }
                        }
                        // TODO: How to verify LocaleData.ES_AUXILIARY ???
                        if(existsInScript == false && h == 0){
                            errln("ExemplarSet containment failed for locale,option,type : "+ locale + ", " + option + ", " + type);
                        }
                    }
                }
            }
            // This is expensive, so only do it if it will be visible
            if (isVerbose()) {
                logln(locale.toString() + " exemplar(ES_STANDARD)" + exemplarSets[0]);
                logln(locale.toString() + " exemplar(ES_AUXILIARY) " + exemplarSets[1]);
                logln(locale.toString() + " exemplar(case-folded,ES_STANDARD) " + exemplarSets[2]);
                logln(locale.toString() + " exemplar(case-folded,ES_AUXILIARY) " + exemplarSets[3]);
            }
            assertTrue(locale.toString() + " case-folded is a superset",
                    exemplarSets[2].containsAll(exemplarSets[0]));
            assertTrue(locale.toString() + " case-folded is a superset",
                    exemplarSets[3].containsAll(exemplarSets[1]));
            if (exemplarSets[2].equals(exemplarSets[0])) {
                ++equalCount;
            }
            if (exemplarSets[3].equals(exemplarSets[1])) {
                ++equalCount;
            }
        }
        // Note: The case-folded set should sometimes be a strict superset
        // and sometimes be equal.
        assertTrue("case-folded is sometimes a strict superset, and sometimes equal",
                equalCount > 0 && equalCount < availableLocales.length * 2);
    }

    // Test case created for checking type coverage of static getExemplarSet method.
    // See #9785, #9794 and #9795
    public void TestExemplarSetTypes() {
        final String[] testLocales = {
            "am",   // No auxiliary / index exemplars as of ICU 50
            "en",
            "th",   // #9785
            "foo",  // Bogus locale
        };

        final int[] testTypes = {
            LocaleData.ES_STANDARD,
            LocaleData.ES_AUXILIARY,
            LocaleData.ES_INDEX,
            LocaleData.ES_CURRENCY,
            LocaleData.ES_PUNCTUATION,
        };

        final String[] testTypeNames = {
                "ES_STANDARD",
                "ES_AUXILIARY",
                "ES_INDEX",
                "ES_CURRENCY",
                "ES_PUNCTUATION",
            };

        for (String locstr : testLocales) {
            ULocale loc = new ULocale(locstr);
            for (int i = 0; i < testTypes.length; i++) {
                try {
                    UnicodeSet set = LocaleData.getExemplarSet(loc, 0, testTypes[i]);
                    if (set == null) {
                        // Not sure null is really OK (#9795)
                        logln(loc + "(" + testTypeNames[i] + ") returned null");
                    } else if (set.isEmpty()) {
                        // This is probably reasonable when data is absent
                        logln(loc + "(" + testTypeNames[i] + ") returned an empty set");
                    }
                } catch (Exception e) {
                    errln(loc + "(" + testTypeNames[i] + ") Exception:" + e.getMessage());
                }
            }
        }
    }

    public void TestCoverage(){
        LocaleData ld = LocaleData.getInstance();
        boolean t = ld.getNoSubstitute();
        ld.setNoSubstitute(t);
        assertEquals("LocaleData get/set NoSubstitute",
                t,
                ld.getNoSubstitute());
    
        logln(ld.getDelimiter(LocaleData.QUOTATION_START));
        logln(ld.getDelimiter(LocaleData.QUOTATION_END));
        logln(ld.getDelimiter(LocaleData.ALT_QUOTATION_START));
        logln(ld.getDelimiter(LocaleData.ALT_QUOTATION_END));
    }

    public void TestFallback(){
        LocaleData fr_FR = LocaleData.getInstance(ULocale.FRANCE);
        LocaleData fr_CA = LocaleData.getInstance(ULocale.CANADA_FRENCH);

        // This better not crash when only some values are overridden
        assertEquals("Start quotes are not equal", fr_FR.getDelimiter(LocaleData.QUOTATION_START), fr_CA.getDelimiter(LocaleData.QUOTATION_START));
        assertEquals("End quotes are not equals", fr_FR.getDelimiter(LocaleData.QUOTATION_END), fr_CA.getDelimiter(LocaleData.QUOTATION_END));
        assertNotEquals("Alt start quotes are equal", fr_FR.getDelimiter(LocaleData.ALT_QUOTATION_START), fr_CA.getDelimiter(LocaleData.ALT_QUOTATION_START));
        assertNotEquals("Alt end quotes are equals", fr_FR.getDelimiter(LocaleData.ALT_QUOTATION_END), fr_CA.getDelimiter(LocaleData.ALT_QUOTATION_END));
    }

    public void TestLocaleDisplayPattern(){
        ULocale locale = ULocale.ENGLISH;
        LocaleData ld = LocaleData.getInstance(locale);
        String pattern = ld.getLocaleDisplayPattern();
        String separator = ld.getLocaleSeparator();
        logln("LocaleDisplayPattern for locale " + locale + ": " + pattern);
        if (!pattern.equals("{0} ({1})")) {
          errln("Unexpected LocaleDisplayPattern for locale: "+ locale);
        }
        logln("LocaleSeparator for locale " + locale + ": " + separator);
        if (!separator.equals(", ")) {
          errln("Unexpected LocaleSeparator for locale: "+ locale);
        }

        locale = ULocale.CHINESE;
        ld = LocaleData.getInstance(locale);
        pattern = ld.getLocaleDisplayPattern();
        separator = ld.getLocaleSeparator();
        logln("LocaleDisplayPattern for locale " + locale + ": " + pattern);
        if (!pattern.equals("{0}\uFF08{1}\uFF09")) {
          errln("Unexpected LocaleDisplayPattern for locale: "+ locale);
        }
        logln("LocaleSeparator for locale " + locale + ": " + separator);
        if (!separator.equals("\u3001")) {
          errln("Unexpected LocaleSeparator for locale: "+ locale);
        }

        for(int i = 0; i < availableLocales.length; i++){
          locale = availableLocales[i];
          ld = LocaleData.getInstance(locale);
          logln(locale.toString() + " LocaleDisplayPattern:" + ld.getLocaleDisplayPattern());
          logln(locale.toString() + " LocaleSeparator:" + ld.getLocaleSeparator());
        }
    }
}
