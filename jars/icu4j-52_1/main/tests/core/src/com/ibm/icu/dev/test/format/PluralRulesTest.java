/*
 *******************************************************************************
 * Copyright (C) 2007-2013, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package com.ibm.icu.dev.test.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.dev.test.serializable.SerializableTest;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.FixedDecimal;
import com.ibm.icu.text.PluralRules.FixedDecimalRange;
import com.ibm.icu.text.PluralRules.FixedDecimalSamples;
import com.ibm.icu.text.PluralRules.KeywordStatus;
import com.ibm.icu.text.PluralRules.PluralType;
import com.ibm.icu.text.PluralRules.SampleType;
import com.ibm.icu.text.UFieldPosition;
import com.ibm.icu.util.Output;
import com.ibm.icu.util.ULocale;

/**
 * @author dougfelt (Doug Felt)
 * @author markdavis (Mark Davis) [for fractional support]
 */
public class PluralRulesTest extends TestFmwk {

    static boolean USE_ALT = System.getProperty("alt_plurals") != null;

    PluralRulesFactory factory = PluralRulesFactory.NORMAL;

    public static void main(String[] args) throws Exception {
        new PluralRulesTest().run(args);
    }

    public void testSyntaxRestrictions() {
        Object[][] shouldFail = {
                {"a:n in 3..10,13..19"},

                // = and != always work
                {"a:n=1"},
                {"a:n=1,3"},
                {"a:n!=1"},
                {"a:n!=1,3"},

                // with spacing
                {"a: n = 1"},
                {"a: n = 1, 3"},
                {"a: n != 1"},
                {"a: n != 1, 3"},
                {"a: n ! = 1"},
                {"a: n ! = 1, 3"},
                {"a: n = 1 , 3"},
                {"a: n != 1 , 3"},
                {"a: n ! = 1 , 3"},
                {"a: n = 1 .. 3"},
                {"a: n != 1 .. 3"},
                {"a: n ! = 1 .. 3"},
                
                // more complicated
                {"a:n in 3 .. 10 , 13 .. 19"},

                // singles have special exceptions
                {"a: n is 1"},
                {"a: n is not 1"},
                {"a: n not is 1", ParseException.class}, // hacked to fail
                {"a: n in 1"},
                {"a: n not in 1"},
                
                // multiples also have special exceptions
                // TODO enable the following once there is an update to CLDR
                // {"a: n is 1,3", ParseException.class},
                {"a: n is not 1,3", ParseException.class}, // hacked to fail
                {"a: n not is 1,3", ParseException.class}, // hacked to fail
                {"a: n in 1,3"},
                {"a: n not in 1,3"},

                // disallow not with =
                {"a: n not= 1", ParseException.class}, // hacked to fail
                {"a: n not= 1,3", ParseException.class}, // hacked to fail

                // disallow double negatives
                {"a: n ! is not 1", ParseException.class},
                {"a: n ! is not 1", ParseException.class},
                {"a: n not not in 1", ParseException.class},
                {"a: n is not not 1", NumberFormatException.class},

                // disallow screwy cases
                {null, NullPointerException.class},
                {"djkl;", ParseException.class},
                {"a: n = 1 .", ParseException.class},
                {"a: n = 1 ..", ParseException.class},
                {"a: n = 1 2", ParseException.class},
                {"a: n = 1 ,", ParseException.class},
                {"a:n in 3 .. 10 , 13 .. 19 ,", ParseException.class},
        };
        for (Object[] shouldFailTest : shouldFail) {
            String rules = (String) shouldFailTest[0];
            Class exception = shouldFailTest.length < 2 ? null : (Class) shouldFailTest[1];
            Class actualException = null;
            try {
                PluralRules.parseDescription(rules);
            } catch (Exception e) {
                actualException = e.getClass();
            }
            assertEquals("Exception " + rules, exception, actualException);
        }
    }
    public void testSamples() {
        String description = "one: n is 3 or f is 5 @integer  3,19, @decimal 3.50 ~ 3.53,   …; other:  @decimal 99.0~99.2, 999.0, …";
        PluralRules test = PluralRules.createRules(description);

        checkNewSamples(description, test, "one", PluralRules.SampleType.INTEGER, "@integer 3, 19", true, new FixedDecimal(3));
        checkNewSamples(description, test, "one", PluralRules.SampleType.DECIMAL, "@decimal 3.50~3.53, …", false, new FixedDecimal(3.5,2));
        checkOldSamples(description, test, "one", SampleType.INTEGER, 3d, 19d);
        checkOldSamples(description, test, "one", SampleType.DECIMAL, 3.5d, 3.51d, 3.52d, 3.53d);

        checkNewSamples(description, test, "other", PluralRules.SampleType.INTEGER, "", true, null);
        checkNewSamples(description, test, "other", PluralRules.SampleType.DECIMAL, "@decimal 99.0~99.2, 999.0, …", false, new FixedDecimal(99d,1));
        checkOldSamples(description, test, "other", SampleType.INTEGER);
        checkOldSamples(description, test, "other", SampleType.DECIMAL, 99d, 99.1, 99.2d, 999d);
    }

    public void checkOldSamples(String description, PluralRules rules, String keyword, SampleType sampleType, Double... expected) {
        Collection<Double> oldSamples = rules.getSamples(keyword, sampleType);
        if (!assertEquals("getOldSamples; " + keyword + "; " + description, new HashSet(Arrays.asList(expected)), oldSamples)) {
            rules.getSamples(keyword, sampleType);
        }
    }

    public void checkNewSamples(String description, PluralRules test, String keyword, SampleType sampleType, 
            String samplesString, boolean isBounded, FixedDecimal firstInRange) {
        String title = description + ", " + sampleType;
        FixedDecimalSamples samples = test.getDecimalSamples(keyword, sampleType);
        if (samples != null) {
            assertEquals("samples; " + title, samplesString, samples.toString());
            assertEquals("bounded; " + title, isBounded, samples.bounded);
            assertEquals("first; " + title, firstInRange, samples.samples.iterator().next().start);
        }
        assertEquals("limited: " + title, isBounded, test.isLimited(keyword, sampleType));
    }

    private static final String[] parseTestData = {
        "a: n is 1", "a:1",
        "a: n mod 10 is 2", "a:2,12,22",
        "a: n is not 1", "a:0,2,3,4,5",
        "a: n mod 3 is not 1", "a:0,2,3,5,6,8,9",
        "a: n in 2..5", "a:2,3,4,5",
        "a: n within 2..5", "a:2,3,4,5",
        "a: n not in 2..5", "a:0,1,6,7,8",
        "a: n not within 2..5", "a:0,1,6,7,8",
        "a: n mod 10 in 2..5", "a:2,3,4,5,12,13,14,15,22,23,24,25",
        "a: n mod 10 within 2..5", "a:2,3,4,5,12,13,14,15,22,23,24,25",
        "a: n mod 10 is 2 and n is not 12", "a:2,22,32,42",
        "a: n mod 10 in 2..3 or n mod 10 is 5", "a:2,3,5,12,13,15,22,23,25",
        "a: n mod 10 within 2..3 or n mod 10 is 5", "a:2,3,5,12,13,15,22,23,25",
        "a: n is 1 or n is 4 or n is 23", "a:1,4,23",
        "a: n mod 2 is 1 and n is not 3 and n in 1..11", "a:1,5,7,9,11",
        "a: n mod 2 is 1 and n is not 3 and n within 1..11", "a:1,5,7,9,11",
        "a: n mod 2 is 1 or n mod 5 is 1 and n is not 6", "a:1,3,5,7,9,11,13,15,16",
        "a: n in 2..5; b: n in 5..8; c: n mod 2 is 1", "a:2,3,4,5;b:6,7,8;c:1,9,11",
        "a: n within 2..5; b: n within 5..8; c: n mod 2 is 1", "a:2,3,4,5;b:6,7,8;c:1,9,11",
        "a: n in 2,4..6; b: n within 7..9,11..12,20", "a:2,4,5,6;b:7,8,9,11,12,20",
        "a: n in 2..8,12 and n not in 4..6", "a:2,3,7,8,12",
        "a: n mod 10 in 2,3,5..7 and n is not 12", "a:2,3,5,6,7,13,15,16,17",
        "a: n in 2..6,3..7", "a:2,3,4,5,6,7",
    };

    private String[] getTargetStrings(String targets) {
        List list = new ArrayList(50);
        String[] valSets = Utility.split(targets, ';');
        for (int i = 0; i < valSets.length; ++i) {
            String[] temp = Utility.split(valSets[i], ':');
            String key = temp[0].trim();
            String[] vals = Utility.split(temp[1], ',');
            for (int j = 0; j < vals.length; ++j) {
                String valString = vals[j].trim();
                int val = Integer.parseInt(valString);
                while (list.size() <= val) {
                    list.add(null);
                }
                if (list.get(val) != null) {
                    fail("test data error, key: " + list.get(val) + " already set for: " + val);
                }
                list.set(val, key);
            }
        }

        String[] result = (String[]) list.toArray(new String[list.size()]);
        for (int i = 0; i < result.length; ++i) {
            if (result[i] == null) {
                result[i] = "other";
            }
        }
        return result;
    }

    private void checkTargets(PluralRules rules, String[] targets) {
        for (int i = 0; i < targets.length; ++i) {
            assertEquals("value " + i, targets[i], rules.select(i));
        }
    }

    public void testParseEmpty() throws ParseException {
        PluralRules rules = PluralRules.parseDescription("a:n");
        assertEquals("empty", "a", rules.select(0));
    }

    public void testParsing() {
        for (int i = 0; i < parseTestData.length; i += 2) {
            String pattern = parseTestData[i];
            String expected = parseTestData[i + 1];

            logln("pattern[" + i + "] " + pattern);
            try {
                PluralRules rules = PluralRules.createRules(pattern);
                String[] targets = getTargetStrings(expected);
                checkTargets(rules, targets);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    private static String[][] operandTestData = {
        {"a: n 3", "FAIL"},
        {"a: n=1,2; b: n != 3..5; c:n!=5", "a:1,2; b:6,7; c:3,4"},
        {"a: n=1,2; b: n!=3..5; c:n!=5", "a:1,2; b:6,7; c:3,4"},
        {"a: t is 1", "a:1.1,1.1000,99.100; other:1.2,1.0"},
        {"a: f is 1", "a:1.1; other:1.1000,99.100"},
        {"a: i is 2; b:i is 3", 
        "b: 3.5; a: 2.5"},
        {"a: f is 0; b:f is 50", 
        "a: 1.00; b: 1.50"},
        {"a: v is 1; b:v is 2", 
        "a: 1.0; b: 1.00"},
        {"one: n is 1 AND v is 0", 
        "one: 1 ; other: 1.00,1.0"}, // English rules
        {"one: v is 0 and i mod 10 is 1 or f mod 10 is 1", 
        "one: 1, 1.1, 3.1; other: 1.0, 3.2, 5"}, // Last visible digit
        {"one: j is 0", 
        "one: 0; other: 0.0, 1.0, 3"}, // Last visible digit
        // one → n is 1; few → n in 2..4;
    };

    public void testOperands() {
        for (String[] pair : operandTestData) {
            String pattern = pair[0].trim();
            String categoriesAndExpected = pair[1].trim();

            //            logln("pattern[" + i + "] " + pattern);
            boolean FAIL_EXPECTED = categoriesAndExpected.equalsIgnoreCase("fail");
            try {
                logln(pattern);
                PluralRules rules = PluralRules.createRules(pattern);
                if (FAIL_EXPECTED) {
                    assertNull("Should fail with 'null' return.", rules);
                } else {
                    logln(rules == null ? "null rules" : rules.toString());
                    checkCategoriesAndExpected(pattern, categoriesAndExpected, rules);
                }
            } catch (Exception e) {
                if (!FAIL_EXPECTED) {
                    e.printStackTrace();
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
    }

    public void testUniqueRules() {
        main:
            for (ULocale locale : factory.getAvailableULocales()) {
                PluralRules rules = factory.forLocale(locale);
                Map<String,PluralRules> keywordToRule = new HashMap<String,PluralRules>();
                Collection<FixedDecimalSamples> samples = new LinkedHashSet<FixedDecimalSamples>();

                for (String keyword : rules.getKeywords()) {
                    for (SampleType sampleType : SampleType.values()) {
                        FixedDecimalSamples samples2 = rules.getDecimalSamples(keyword, sampleType);
                        if (samples2 != null) {
                            samples.add(samples2);
                        }
                    }
                    if (keyword.equals("other")) {
                        continue;
                    }
                    String rules2 = keyword + ":" + rules.getRules(keyword);
                    PluralRules singleRule = PluralRules.createRules(rules2);
                    if (singleRule == null) {
                        errln("Can't generate single rule for " + rules2);
                        PluralRules.createRules(rules2); // for debugging
                        continue main;
                    }
                    keywordToRule.put(keyword, singleRule);
                }
                Map<FixedDecimal, String> collisionTest = new TreeMap();
                for (FixedDecimalSamples sample3 : samples) {
                    Set<FixedDecimalRange> samples2 = sample3.getSamples();
                    if (samples2 == null) {
                        continue;
                    }
                    for (FixedDecimalRange sample : samples2) {
                        for (int i = 0; i < 1; ++i) {
                            FixedDecimal item = i == 0 ? sample.start : sample.end;
                            collisionTest.clear();
                            for (Entry<String, PluralRules> entry: keywordToRule.entrySet()) {
                                PluralRules rule = entry.getValue();
                                String foundKeyword = rule.select(item);
                                if (foundKeyword.equals("other")) {
                                    continue;
                                }
                                String old = collisionTest.get(item);
                                if (old != null) {
                                    errln(locale + "\tNon-unique rules: " + item + " => " + old + " & " + foundKeyword);
                                    rule.select(item);
                                } else {
                                    collisionTest.put(item, foundKeyword);
                                }
                            }
                        }
                    }
                }
            }
    }

    private void checkCategoriesAndExpected(String title1, String categoriesAndExpected, PluralRules rules) {
        for (String categoryAndExpected : categoriesAndExpected.split("\\s*;\\s*")) {
            String[] categoryFromExpected = categoryAndExpected.split("\\s*:\\s*");
            String expected = categoryFromExpected[0];
            for (String value : categoryFromExpected[1].split("\\s*,\\s*")) {
                double number = Double.parseDouble(value);
                int decimalPos = value.indexOf('.') + 1;
                int countVisibleFractionDigits;
                int fractionaldigits;
                if (decimalPos == 0) {
                    countVisibleFractionDigits = fractionaldigits = 0;
                } else {
                    countVisibleFractionDigits = value.length() - decimalPos;
                    fractionaldigits = Integer.parseInt(value.substring(decimalPos));
                }
                String result = rules.select(number, countVisibleFractionDigits, fractionaldigits);
                ULocale locale = null;
                assertEquals(getAssertMessage(title1, locale, rules, expected) + "; value: " + value, expected, result);
            }
        }
    }

    private static String[][] equalityTestData = {
        // once we add fractions, we had to retract the "test all possibilities" for equality,
        // so we only have a limited set of equality tests now.
        { "c: n%11!=5", "c: n mod 11 is not 5" },
        { "c: n is not 7", "c: n != 7" },
        { "a:n in 2;", "a: n = 2" },
        { "b:n not in 5;", "b: n != 5" },

        //        { "a: n is 5",
        //        "a: n in 2..6 and n not in 2..4 and n is not 6" },
        //        { "a: n in 2..3",
        //            "a: n is 2 or n is 3",
        //        "a: n is 3 and n in 2..5 or n is 2" },
        //        { "a: n is 12; b:n mod 10 in 2..3",
        //            "b: n mod 10 in 2..3 and n is not 12; a: n in 12..12",
        //        "b: n is 13; a: n is 12; b: n mod 10 is 2 or n mod 10 is 3" },
    };

    private static String[][] inequalityTestData = {
        { "a: n mod 8 is 3",
            "a: n mod 7 is 3"
        },
        { "a: n mod 3 is 2 and n is not 5",
            "a: n mod 6 is 2 or n is 8 or n is 11"
        },
        // the following are currently inequal, but we may make them equal in the future.
        { "a: n in 2..5",
        "a: n in 2..4,5" },
    };

    private void compareEquality(String id, Object[] objects, boolean shouldBeEqual) {
        for (int i = 0; i < objects.length; ++i) {
            Object lhs = objects[i];
            int start = shouldBeEqual ? i : i + 1;
            for (int j = start; j < objects.length; ++j) {
                Object rhs = objects[j];
                if (rhs == null || shouldBeEqual != lhs.equals(rhs)) {
                    String msg = shouldBeEqual ? "should be equal" : "should not be equal";
                    fail(id + " " + msg + " (" + i + ", " + j + "):\n    " + lhs + "\n    " + rhs);
                }
                // assertEquals("obj " + i + " and " + j, lhs, rhs);
            }
        }
    }

    private void compareEqualityTestSets(String[][] sets, boolean shouldBeEqual) {
        for (int i = 0; i < sets.length; ++i) {
            String[] patterns = sets[i];
            PluralRules[] rules = new PluralRules[patterns.length];
            for (int j = 0; j < patterns.length; ++j) {
                rules[j] = PluralRules.createRules(patterns[j]);
            }
            compareEquality("test " + i, rules, shouldBeEqual);
        }
    }

    public void testEquality() {
        compareEqualityTestSets(equalityTestData, true);
    }

    public void testInequality() {
        compareEqualityTestSets(inequalityTestData, false);
    }

    public void testBuiltInRules() {
        // spot check
        PluralRules rules = factory.forLocale(ULocale.US);
        assertEquals("us 0", PluralRules.KEYWORD_OTHER, rules.select(0));
        assertEquals("us 1", PluralRules.KEYWORD_ONE, rules.select(1));
        assertEquals("us 2", PluralRules.KEYWORD_OTHER, rules.select(2));

        rules = factory.forLocale(ULocale.JAPAN);
        assertEquals("ja 0", PluralRules.KEYWORD_OTHER, rules.select(0));
        assertEquals("ja 1", PluralRules.KEYWORD_OTHER, rules.select(1));
        assertEquals("ja 2", PluralRules.KEYWORD_OTHER, rules.select(2));

        rules = factory.forLocale(ULocale.createCanonical("ru"));
        assertEquals("ru 0", PluralRules.KEYWORD_MANY, rules.select(0));
        assertEquals("ru 1", PluralRules.KEYWORD_ONE, rules.select(1));
        assertEquals("ru 2", PluralRules.KEYWORD_OTHER, rules.select(2));
    }

    public void testFunctionalEquivalent() {
        // spot check
        ULocale unknown = ULocale.createCanonical("zz_ZZ");
        ULocale un_equiv = PluralRules.getFunctionalEquivalent(unknown, null);
        assertEquals("unknown locales have root", ULocale.ROOT, un_equiv);

        ULocale jp_equiv = PluralRules.getFunctionalEquivalent(ULocale.JAPAN, null);
        ULocale cn_equiv = PluralRules.getFunctionalEquivalent(ULocale.CHINA, null);
        assertEquals("japan and china equivalent locales", jp_equiv, cn_equiv);

        boolean[] available = new boolean[1];
        ULocale russia = ULocale.createCanonical("ru_RU");
        ULocale ru_ru_equiv = PluralRules.getFunctionalEquivalent(russia, available);
        assertFalse("ru_RU not listed", available[0]);

        ULocale russian = ULocale.createCanonical("ru");
        ULocale ru_equiv = PluralRules.getFunctionalEquivalent(russian, available);
        assertTrue("ru listed", available[0]);
        assertEquals("ru and ru_RU equivalent locales", ru_ru_equiv, ru_equiv);
    }

    public void testAvailableULocales() {
        ULocale[] locales = factory.getAvailableULocales();
        Set localeSet = new HashSet();
        localeSet.addAll(Arrays.asList(locales));

        assertEquals("locales are unique in list", locales.length, localeSet.size());
    }

    /*
     * Test the method public static PluralRules parseDescription(String description)
     */
    public void TestParseDescription() {
        try {
            if (PluralRules.DEFAULT != PluralRules.parseDescription("")) {
                errln("PluralRules.parseDescription(String) was suppose "
                        + "to return PluralRules.DEFAULT when String is of " + "length 0.");
            }
        } catch (ParseException e) {
            errln("PluralRules.parseDescription(String) was not suppose " + "to return an exception.");
        }
    }

    /*
     * Tests the method public static PluralRules createRules(String description)
     */
    public void TestCreateRules() {
        try {
            if (PluralRules.createRules(null) != null) {
                errln("PluralRules.createRules(String) was suppose to "
                        + "return null for an invalid String descrtiption.");
            }
        } catch (Exception e) {
        }
    }

    /*
     * Tests the method public int hashCode()
     */
    public void TestHashCode() {
        // Bad test, breaks whenever PluralRules implementation changes.
        //        PluralRules pr = PluralRules.DEFAULT;
        //        if (106069776 != pr.hashCode()) {
        //            errln("PluralRules.hashCode() was suppose to return 106069776 " + "when PluralRules.DEFAULT.");
        //        }
    }

    /*
     * Tests the method public boolean equals(PluralRules rhs)
     */
    public void TestEquals() {
        PluralRules pr = PluralRules.DEFAULT;

        if (pr.equals((PluralRules) null)) {
            errln("PluralRules.equals(PluralRules) was supposed to return false " + "when passing null.");
        }
    }

    private void assertRuleValue(String rule, double value) {
        assertRuleKeyValue("a:" + rule, "a", value);
    }

    private void assertRuleKeyValue(String rule, String key, double value) {
        PluralRules pr = PluralRules.createRules(rule);
        assertEquals(rule, value, pr.getUniqueKeywordValue(key));
    }

    /*
     * Tests getUniqueKeywordValue()
     */
    public void TestGetUniqueKeywordValue() {
        assertRuleKeyValue("a: n is 1", "not_defined", PluralRules.NO_UNIQUE_VALUE); // key not defined
        assertRuleValue("n within 2..2", 2);
        assertRuleValue("n is 1", 1);
        assertRuleValue("n in 2..2", 2);
        assertRuleValue("n in 3..4", PluralRules.NO_UNIQUE_VALUE);
        assertRuleValue("n within 3..4", PluralRules.NO_UNIQUE_VALUE);
        assertRuleValue("n is 2 or n is 2", 2);
        assertRuleValue("n is 2 and n is 2", 2);
        assertRuleValue("n is 2 or n is 3", PluralRules.NO_UNIQUE_VALUE);
        assertRuleValue("n is 2 and n is 3", PluralRules.NO_UNIQUE_VALUE);
        assertRuleValue("n is 2 or n in 2..3", PluralRules.NO_UNIQUE_VALUE);
        assertRuleValue("n is 2 and n in 2..3", 2);
        assertRuleKeyValue("a: n is 1", "other", PluralRules.NO_UNIQUE_VALUE); // key matches default rule
        assertRuleValue("n in 2,3", PluralRules.NO_UNIQUE_VALUE);
        assertRuleValue("n in 2,3..6 and n not in 2..3,5..6", 4);
    }

    /**
     * The version in PluralFormatUnitTest is not really a test, and it's in the wrong place
     * anyway, so I'm putting a variant of it here.
     */
    public void TestGetSamples() {
        Set<ULocale> uniqueRuleSet = new HashSet<ULocale>();
        for (ULocale locale : factory.getAvailableULocales()) {
            uniqueRuleSet.add(PluralRules.getFunctionalEquivalent(locale, null));
        }
        for (ULocale locale : uniqueRuleSet) {
            PluralRules rules = factory.forLocale(locale);
            logln("\nlocale: " + (locale == ULocale.ROOT ? "root" : locale.toString()) + ", rules: " + rules);
            Set<String> keywords = rules.getKeywords();
            for (String keyword : keywords) {
                Collection<Double> list = rules.getSamples(keyword);
                logln("keyword: " + keyword + ", samples: " + list);
                // with fractions, the samples can be empty and thus the list null. In that case, however, there will be FixedDecimal values.
                // So patch the test for that.
                if (list.size() == 0) {
                    // when the samples (meaning integer samples) are null, then then integerSamples must be, and the decimalSamples must not be
                    FixedDecimalSamples integerSamples = rules.getDecimalSamples(keyword, SampleType.INTEGER);
                    FixedDecimalSamples decimalSamples = rules.getDecimalSamples(keyword, SampleType.DECIMAL);
                    assertTrue(getAssertMessage("List is not null", locale, rules, keyword), 
                            integerSamples == null && decimalSamples != null && decimalSamples.samples.size() != 0);
                } else {
                    if (!assertTrue(getAssertMessage("Test getSamples.isEmpty", locale, rules, keyword), !list.isEmpty())) {
                        rules.getSamples(keyword);
                    }
                    if (rules.toString().contains(": j")) {
                        // hack until we remove j
                    } else {
                        for (double value : list) {
                            assertEquals(getAssertMessage("Match keyword", locale, rules, keyword) +  "; value '" + value + "'", keyword, rules.select(value));
                        }
                    }
                }
            }

            assertNull(locale + ", list is null", rules.getSamples("@#$%^&*"));
            assertNull(locale + ", list is null", rules.getSamples("@#$%^&*", SampleType.DECIMAL));
        }
    }

    public String getAssertMessage(String message, ULocale locale, PluralRules rules, String keyword) {
        String ruleString = "";
        if (keyword != null) { 
            if (keyword.equals("other")) {
                for (String keyword2 : rules.getKeywords()) {
                    ruleString += " NOR " + rules.getRules(keyword2).split("@")[0];
                }
            } else {
                String rule = rules.getRules(keyword);
                ruleString = rule == null ? null : rule.split("@")[0];
            }
            ruleString =  "; rule: '" + keyword + ": " + ruleString + "'";
            //            !keyword.equals("other") ? "'; keyword: '" + keyword + "'; rule: '" + rules.getRules(keyword) + "'"
            //                    : "'; keyword: '" + keyword + "'; rules: '" + rules.toString() + "'";
        }
        return message 
                + (locale == null ? "" : "; locale: '" + locale + "'")
                + ruleString;
    }

    /**
     * Returns the empty set if the keyword is not defined, null if there are an unlimited
     * number of values for the keyword, or the set of values that trigger the keyword.
     */
    public void TestGetAllKeywordValues() {
        // data is pairs of strings, the rule, and the expected values as arguments
        String[] data = {
                "other: ; a: n mod 3 is 0", "a: null",
                "a: n in 2..5 and n within 5..8", "a: 5",
                "a: n in 2..5", "a: 2,3,4,5; other: null",
                "a: n not in 2..5", "a: null; other: null",
                "a: n within 2..5", "a: 2,3,4,5; other: null",
                "a: n not within 2..5", "a: null; other: null",
                "a: n in 2..5 or n within 6..8", "a: 2,3,4,5,6,7,8", // ignore 'other' here on out, always null
                "a: n in 2..5 and n within 6..8", "a: null",
                // we no longer support 'degenerate' rules
                //                "a: n within 2..5 and n within 6..8", "a:", // our sampling catches these
                //                "a: n within 2..5 and n within 5..8", "a: 5", // ''
                //                "a: n within 1..2 and n within 2..3 or n within 3..4 and n within 4..5", "a: 2,4",
                //                "a: n mod 3 is 0 and n within 0..5", "a: 0,3",
                "a: n within 1..2 and n within 2..3 or n within 3..4 and n within 4..5 or n within 5..6 and n within 6..7", "a: 2,4,6", // but not this...
                "a: n mod 3 is 0 and n within 1..2", "a: null",
                "a: n mod 3 is 0 and n within 0..6", "a: 0,3,6",
                "a: n mod 3 is 0 and n in 3..12", "a: 3,6,9,12",
                "a: n in 2,4..6 and n is not 5", "a: 2,4,6",
        };
        for (int i = 0; i < data.length; i += 2) {
            String ruleDescription = data[i];
            String result = data[i+1];

            PluralRules p = PluralRules.createRules(ruleDescription);
            if (p == null) { // for debugging
                PluralRules.createRules(ruleDescription);
            }
            for (String ruleResult : result.split(";")) {
                String[] ruleAndValues = ruleResult.split(":");
                String keyword = ruleAndValues[0].trim();
                String valueList = ruleAndValues.length < 2 ? null : ruleAndValues[1];
                if (valueList != null) {
                    valueList = valueList.trim();
                }
                Collection<Double> values;
                if (valueList == null || valueList.length() == 0) {
                    values = Collections.EMPTY_SET;
                } else if ("null".equals(valueList)) {
                    values = null;
                } else {
                    values = new TreeSet<Double>();
                    for (String value : valueList.split(",")) {
                        values.add(Double.parseDouble(value));
                    }
                }

                Collection<Double> results = p.getAllKeywordValues(keyword);
                assertEquals(keyword + " in " + ruleDescription, values, results == null ? null : new HashSet(results));

                if (results != null) {
                    try {
                        results.add(PluralRules.NO_UNIQUE_VALUE);
                        fail("returned set is modifiable");
                    } catch (UnsupportedOperationException e) {
                        // pass
                    }
                }
            }
        }
    }

    public void TestOrdinal() {
        PluralRules pr = factory.forLocale(ULocale.ENGLISH, PluralType.ORDINAL);
        assertEquals("PluralRules(en-ordinal).select(2)", "two", pr.select(2));
    }

    public void TestBasicFraction() {
        String[][] tests = {
                {"en", "one: j is 1"},
                {"1", "0", "1", "one"},                
                {"1", "2", "1.00", "other"},                
        };
        ULocale locale = null;
        NumberFormat nf = null;
        PluralRules pr = null;

        for (String[] row : tests) {
            switch(row.length) {
            case 2:
                locale = ULocale.forLanguageTag(row[0]);
                nf = NumberFormat.getInstance(locale);
                pr = PluralRules.createRules(row[1]);
                break;
            case 4:
                double n = Double.parseDouble(row[0]);
                int minFracDigits = Integer.parseInt(row[1]);
                nf.setMinimumFractionDigits(minFracDigits);
                String expectedFormat = row[2];
                String expectedKeyword = row[3];
                
                UFieldPosition pos = new UFieldPosition();
                String formatted = nf.format(1.0, new StringBuffer(), pos).toString();
                int countVisibleFractionDigits = pos.getCountVisibleFractionDigits();
                long fractionDigits = pos.getFractionDigits();
                String keyword = pr.select(n, countVisibleFractionDigits, fractionDigits);
                assertEquals("Formatted " + n + "\t" + minFracDigits, expectedFormat, formatted);
                assertEquals("Keyword " + n + "\t" + minFracDigits, expectedKeyword, keyword);
                break;
            default:
                throw new RuntimeException();
            }
        }
    }

    public void TestLimitedAndSamplesConsistency() {
        for (ULocale locale : PluralRules.getAvailableULocales()) {
            ULocale loc2 = PluralRules.getFunctionalEquivalent(locale, null);
            if (!loc2.equals(locale)) {
                continue; // only need "unique" rules
            }
            for (PluralType type : PluralType.values()) {
                PluralRules rules = PluralRules.forLocale(locale, type);
                for (SampleType sampleType : SampleType.values()) {
                    for (String keyword : rules.getKeywords()) {
                        boolean isLimited = rules.isLimited(keyword, sampleType);
                        boolean computeLimited = rules.computeLimited(keyword, sampleType);
                        if (!keyword.equals("other")) {
                            assertEquals(getAssertMessage("computeLimited == isLimited", locale, rules, keyword), computeLimited, isLimited);
                        }
                        Collection<Double> samples = rules.getSamples(keyword, sampleType);
                        assertNotNull(getAssertMessage("Samples must not be null", locale, rules, keyword), samples);
                        /*FixedDecimalSamples decimalSamples = */ rules.getDecimalSamples(keyword, sampleType);
                        //assertNotNull(getAssertMessage("Decimal samples must be null if unlimited", locale, rules, keyword), decimalSamples);
                    }
                }
            }
        }
    }

    public void TestKeywords() {
        Set<String> possibleKeywords = new LinkedHashSet(Arrays.asList("zero", "one", "two", "few", "many", "other"));
        Object[][][] tests = {
                // format is locale, explicits, then triples of keyword, status, unique value.
                {{"en", null}, 
                    {"one", KeywordStatus.UNIQUE, 1.0d}, 
                    {"other", KeywordStatus.UNBOUNDED, null}
                },
                {{"pl", null}, 
                    {"one", KeywordStatus.UNIQUE, 1.0d}, 
                    {"few", KeywordStatus.UNBOUNDED, null}, 
                    {"many", KeywordStatus.UNBOUNDED, null}, 
                    {"other", KeywordStatus.SUPPRESSED, null, KeywordStatus.UNBOUNDED, null} // note that it is suppressed in INTEGER but not DECIMAL
                },
                {{"en", new HashSet<Double>(Arrays.asList(1.0d))}, // check that 1 is suppressed
                    {"one", KeywordStatus.SUPPRESSED, null}, 
                    {"other", KeywordStatus.UNBOUNDED, null}
                },
        };
        Output<Double> uniqueValue = new Output<Double>();
        for (Object[][] test : tests) {
            ULocale locale = new ULocale((String) test[0][0]);
            // NumberType numberType = (NumberType) test[1];
            Set<Double> explicits = (Set<Double>) test[0][1];
            PluralRules pluralRules = factory.forLocale(locale);
            LinkedHashSet<String> remaining = new LinkedHashSet(possibleKeywords);
            for (int i = 1; i < test.length; ++i) {
                Object[] row = test[i];
                String keyword = (String) row[0];
                KeywordStatus statusExpected = (KeywordStatus) row[1];
                Double uniqueExpected = (Double) row[2];
                remaining.remove(keyword);
                KeywordStatus status = pluralRules.getKeywordStatus(keyword, 0, explicits, uniqueValue);
                assertEquals(getAssertMessage("Unique Value", locale, pluralRules, keyword), uniqueExpected, uniqueValue.value);
                assertEquals(getAssertMessage("Keyword Status", locale, pluralRules, keyword), statusExpected, status);
                if (row.length > 3) {
                    statusExpected = (KeywordStatus) row[3];
                    uniqueExpected = (Double) row[4];
                    status = pluralRules.getKeywordStatus(keyword, 0, explicits, uniqueValue, SampleType.DECIMAL);
                    assertEquals(getAssertMessage("Unique Value - decimal", locale, pluralRules, keyword), uniqueExpected, uniqueValue.value);
                    assertEquals(getAssertMessage("Keyword Status - decimal", locale, pluralRules, keyword), statusExpected, status);
                }
            }
            for (String keyword : remaining) {
                KeywordStatus status = pluralRules.getKeywordStatus(keyword, 0, null, uniqueValue);
                assertEquals("Invalid keyword " + keyword, status, KeywordStatus.INVALID);
                assertNull("Invalid keyword " + keyword, uniqueValue.value);
            }
        }
    }

    enum StandardPluralCategories {
        zero,
        one,
        two,
        few,
        many,
        other;
        /**
         * 
         */
        private static final Set<StandardPluralCategories> ALL = Collections.unmodifiableSet(EnumSet.allOf(StandardPluralCategories.class));
        /**
         * Return a mutable set
         * @param source
         * @return
         */
        static final EnumSet<StandardPluralCategories> getSet(Collection<String> source) {
            EnumSet<StandardPluralCategories> result = EnumSet.noneOf(StandardPluralCategories.class);
            for (String s : source) {
                result.add(StandardPluralCategories.valueOf(s));
            }
            return result;
        }
        static final Comparator<Set<StandardPluralCategories>> SHORTEST_FIRST = new Comparator<Set<StandardPluralCategories>>() {
            public int compare(Set<StandardPluralCategories> arg0, Set<StandardPluralCategories> arg1) {
                int diff = arg0.size() - arg1.size();
                if (diff != 0) {
                    return diff;
                }
                // otherwise first...
                // could be optimized, but we don't care here.
                for (StandardPluralCategories value : ALL) {
                    if (arg0.contains(value)) {
                        if (!arg1.contains(value)) {
                            return 1;
                        }
                    } else if (arg1.contains(value)) {
                        return -1;
                    }

                }
                return 0;
            }

        };
    }

    public void TestLocales() {
        if (false) {
            generateLOCALE_SNAPSHOT();
        }
        for (String test : LOCALE_SNAPSHOT) {
            test = test.trim();
            String[] parts = test.split("\\s*;\\s*");
            for (String localeString : parts[0].split("\\s*,\\s*")) {
                ULocale locale = new ULocale(localeString);
                if (factory.hasOverride(locale)) {
                    continue; // skip for now
                }
                PluralRules rules = factory.forLocale(locale);
                for (int i = 1; i < parts.length; ++i) {
                    checkCategoriesAndExpected(localeString, parts[i], rules);
                }
            }
        }
    }

    /**
     * 
     */
    private void generateLOCALE_SNAPSHOT() {
        Comparator c = new CollectionUtilities.CollectionComparator<Comparable>();
        Relation<Set<StandardPluralCategories>,PluralRules> setsToRules 
        = Relation.of(new TreeMap<Set<StandardPluralCategories>,Set<PluralRules>>(c), TreeSet.class);
        Relation<PluralRules,ULocale> data = Relation.of(new TreeMap<PluralRules,Set<ULocale>>(), TreeSet.class);
        for (ULocale locale : PluralRules.getAvailableULocales()) {
            PluralRules pr = PluralRules.forLocale(locale);
            EnumSet<StandardPluralCategories> set = getCanonicalSet(pr.getKeywords());
            setsToRules.put(set, pr);
            data.put(pr, locale);
        }
        for (Entry<Set<StandardPluralCategories>, Set<PluralRules>> entry1 : setsToRules.keyValuesSet()) {
            Set<StandardPluralCategories> set = entry1.getKey();
            Set<PluralRules> rules = entry1.getValue();
            System.out.println("\n        // " + set);
            for (PluralRules rule : rules) {
                Set<ULocale> locales = data.get(rule);
                System.out.print("        \"" + CollectionUtilities.join(locales, ","));
                for (StandardPluralCategories spc : set) {
                    String keyword = spc.toString();
                    FixedDecimalSamples samples = rule.getDecimalSamples(keyword, SampleType.INTEGER);
                    System.out.print("; " + spc + ": " + samples);
                }
                System.out.println("\",");
            }
        }
    }

    /**
     * @param keywords
     * @return
     */
    private EnumSet<StandardPluralCategories> getCanonicalSet(Set<String> keywords) {
        EnumSet<StandardPluralCategories> result = EnumSet.noneOf(StandardPluralCategories.class);
        for (String s : keywords) {
            result.add(StandardPluralCategories.valueOf(s));
        }
        return result;
    }

    static final String[] LOCALE_SNAPSHOT = {
        // [other]
        "bm,bo,dz,id,ig,ii,ja,jv,kde,kea,km,ko,lkt,lo,ms,my,sah,ses,sg,th,to,vi,wo,yo,zh; other: 0, 0.0, 0.00, 0.1, 0.37, 1, 1.99, 2",

        // [one, other]
        "fil,tl; one: 0, 1; other: 0.0, 0.00, 0.03, 0.1, 0.3, 0.30, 1.99, 2, 2.0, 2.00, 2.01, 2.1, 2.10, 3",
        "ca,de,en,et,fi,gl,it,nl,sw,ur,yi; one: 1; other: 0, 0.0, 0.00, 0.01, 0.1, 0.10, 1.0, 1.00, 1.03, 1.3, 1.30, 1.99, 2, 3",
        // danish is now: one: n is 1 or t is not 0 and i is 0,1 @integer 1 @decimal 0.1~0.8
        "da; one: 0.01, 0.1, 1, 0.10, 1.0, 1.00, 1.03, 1.3, 1.30, 1.99; other: 0, 0.0, 0.00, 2, 2.2, 2.9, 3",
        // swedish is now: one: i is 1 and v is 0 @integer 1
        "sv; one: 1; other: 0.01, 0.1, 0, 0.0, 0.00, 0.10, 1.0, 1.00, 1.03, 1.3, 1.30, 1.99, 2, 3",
        // icelandic is now: one: t is 0 and i mod 10 is 1 and i mod 100 is not 11 or t is not 0
        "is; one: 0.1, 0.31, 1, 31, 1.0, 1.00, 1.11, 1.99; other: 0, 0.0, 0.00, 2, 11, 111, 311",
        "mk; one: 0.1, 0.31, 1, 11, 31; other: 0, 0.0, 0.00, 1.0, 1.00, 1.03, 1.3, 1.30, 1.99, 2, 3",
        "ak,bh,guw,ln,mg,nso,pa,ti,wa; one: 0, 0.0, 0.00, 1; other: 0.03, 0.1, 0.3, 0.30, 1.99, 2, 2.0, 2.00, 2.01, 2.1, 2.10, 3",
        "tzm; one: 0, 0.0, 0.00, 1, 11, 99; other: 0.03, 0.1, 0.3, 0.30, 1.99, 2, 2.0, 2.00, 2.11, 3",
        "af,asa,ast,az,bem,bez,bg,brx,cgg,chr,ckb,dv,ee,el,eo,es,eu,fo,fur,fy,gsw,ha,haw,hu,jgo,jmc,ka,kaj,kcg,kk,kkj,kl,ks,ksb,ku,ky,lb,lg,mas,mgo,ml,mn,nah,nb,nd,ne,nn,nnh,no,nr,ny,nyn,om,or,os,pap,ps,rm,rof,rwk,saq,seh,sn,so,sq,ss,ssy,st,syr,ta,te,teo,tig,tk,tn,tr,ts,ve,vo,vun,wae,xh,xog; one: 1, 1.0, 1.00; other: 0, 0.0, 0.00, 0.01, 0.1, 0.10, 1.03, 1.3, 1.30, 1.99, 2, 3",
        // pt is now: i = 1 and v = 0 or i = 0 and t = 1
        "pt; one:  0.1, 0.01, 0.10, 0.001, 0.010, 0.100, 0.0001, 0.0010, 0.0100, 0.1000, 1; other: 0.0, 0.2, 0.3, 1.6, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0",
        "am,bn,fa,gu,hi,kn,mr,zu; one: 0, 0.0, 0.00, 0.03, 0.1, 0.3, 0.30, 0.5, 1; other: 1.99, 2, 2.0, 2.00, 2.01, 2.1, 2.10, 3",
        "ff,fr,hy,kab; one: 0, 0.0, 0.00, 0.02, 0.1, 0.2, 0.20, 1, 1.99; other: 2, 2.0, 2.00, 2.01, 2.1, 2.10",

        // [zero, one, other]
        "ksh; zero: 0, 0.0, 0.00; one: 1, 1.0, 1.00; other: 0.03, 0.1, 0.3, 0.30, 1.03, 1.3, 1.30, 1.99, 2, 2.0, 2.00, 2.01, 2.1, 2.10, 3",
        "lag; zero: 0, 0.0, 0.00; one: 0.02, 0.1, 0.2, 0.20, 1, 1.0, 1.00, 1.02, 1.2, 1.20, 1.99; other: 2, 2.0, 2.00, 2.01, 2.1, 2.10",
        "lv; zero: 0, 0.0, 0.00, 10, 11, 30, 111, 311; one: 0.1, 0.31, 1, 1.0, 1.00, 21, 31, 41; other: 1.30, 1.99, 2, 2.0, 2.00, 2.30, 29, 49",

        // [one, two, other]
        "iu,kw,naq,se,sma,smi,smj,smn,sms; one: 1, 1.0, 1.00; two: 2, 2.0, 2.00; other: 0, 0.0, 0.00, 0.02, 0.1, 0.2, 0.20, 1.04, 1.4, 1.40, 1.99, 2.04, 2.4, 2.40, 3, 4",

        // [one, few, other]
        "mo,ro; one: 1; few: 0, 0.1, 1.0, 1.00, 1.319, 1.99, 2, 21.0, 21.00, 21.319, 101, 119, 119.0, 119.00, 119.20, 301, 319; other: 20, 21",
        "bs,hr,sh,sr; one: 0.1, 0.31, 1, 31, 34.31; few: 2, 32, 34; other: 0, 0.0, 0.00, 1.0, 1.00, 1.112, 1.99, 11, 12, 14, 34.0, 34.00, 111, 112, 114, 311, 312, 314",
        "shi; one: 0, 0.0, 0.00, 0.1, 0.12, 0.5, 1; few: 2, 2.0, 2.00, 10; other: 1.99, 2.12, 11, 11.0, 11.00, 11.10, 12",

        // [one, many, other]
        "ru; one: 1, 31; many: 0, 10, 11, 15, 19, 30, 35, 39, 111, 114, 311, 314; other: 0.0, 0.00, 0.1, 0.31, 1.0, 1.00, 1.30, 1.99, 2, 2.0, 2.00, 2.30, 3",

        // [one, two, few, other]
        "sl; one: 1, 101, 301; two: 2, 102, 302; few: 0.0, 0.00, 0.1, 0.303, 1.0, 1.00, 1.303, 1.99, 102.0, 102.00, 102.303, 103, 104, 303, 304, 304.0, 304.00, 304.302; other: 0",
        "gd; one: 1, 1.0, 1.00, 11; two: 2, 2.0, 2.00, 12; few: 3, 19, 19.0, 19.00; other: 0, 0.0, 0.00, 0.1, 0.12, 1.12, 1.99, 2.11, 19.12, 20, 21",
        "gv; one: 1, 1.0, 1.00, 11, 31; two: 2, 2.0, 2.00, 12, 32; few: 0, 0.0, 0.00, 100, 160, 300, 360; other: 0.1, 0.31, 1.360, 1.99, 2.31, 3, 3.0, 3.00, 3.31, 4",

        // [one, two, many, other]
        "he; one: 1; two: 2; many: 30; other: 0, 0.0, 0.00, 0.1, 0.30, 1.0, 1.00, 1.30, 1.99, 2.0, 2.00, 2.30, 10, 30.0, 30.00, 30.10",

        // [one, few, many, other]
        "cs,sk; one: 1; few: 2, 4; many: 0.0, 0.00, 0.04, 0.1, 0.4, 0.40, 1.0, 1.00, 1.05, 1.5, 1.50, 1.99, 2.0, 2.00, 2.05, 2.5, 2.50; other: 0, 5",
        "pl; one: 1; few: 2, 32, 34; many: 0, 10, 11, 12, 14, 15, 19, 30, 31, 35, 39, 112, 114, 312, 314; other: 0.0, 0.00, 0.1, 0.32, 1.0, 1.00, 1.1, 1.2, 1.30, 1.99, 34.0, 34.00, 34.30",
        "uk; one: 1, 31; few: 2, 32, 34; many: 0, 10, 11, 12, 14, 15, 19, 30, 35, 39, 111, 112, 114, 311, 312, 314; other: 0.0, 0.00, 0.1, 0.31, 1.0, 1.00, 1.1, 1.2, 1.30, 1.99, 34.0, 34.00, 34.30",
        "mt; one: 1, 1.0, 1.00; few: 0, 0.0, 0.00, 2, 102, 110, 302, 310; many: 111, 119, 311, 311.0, 311.00, 319; other: 0.1, 0.20, 1.302, 1.99, 20, 21, 21.0, 21.00, 21.302, 311.302",
        "be; one: 1, 1.0, 1.00, 31; few: 2, 32, 34, 34.0, 34.00; many: 0, 0.0, 0.00, 10, 11, 12, 14, 15, 19, 30, 35, 39, 111, 112, 114, 311, 312, 314; other: 0.1, 0.31, 1.1, 1.2, 1.30, 1.99, 34.30",
        "lt; one: 1, 1.0, 1.00, 31; few: 2, 2.0, 2.00, 32, 39; many: 0.1, 0.31, 1.19, 1.99, 2.31; other: 0, 0.0, 0.00, 10, 11, 12, 19, 111, 119, 311, 319",

        // [one, two, few, many, other]
        "ga; one: 1, 1.0, 1.00; two: 2, 2.0, 2.00; few: 3, 3.0, 3.00, 6; many: 7, 7.0, 7.00, 10; other: 0, 0.0, 0.00, 0.1, 0.10, 1.12, 1.99, 2.12, 3.12, 7.12, 11, 12",
        "br; one: 1, 1.0, 1.00, 31; two: 2, 2.0, 2.00, 32; few: 33, 33.0, 33.00, 39; many: 1000000, 3000000, 3000000.0, 3000000.00; other: 0, 0.0, 0.00, 0.1, 0.39, 1.112, 1.99, 2.112, 11, 12, 13, 19, 33.112, 110, 111, 112, 191, 192, 199, 310, 311, 312, 391, 392, 399, 3000000.112",

        // [zero, one, two, few, many, other]
        "cy; zero: 0, 0.0, 0.00; one: 1, 1.0, 1.00; two: 2, 2.0, 2.00; few: 3, 3.0, 3.00; many: 6, 6.0, 6.00; other: 0.06, 0.1, 0.6, 0.60, 1.06, 1.6, 1.60, 1.99, 2.06, 2.6, 2.60, 3.06, 3.6, 3.60, 4, 4.0, 4.00, 4.06, 4.6, 4.60, 5, 6.05, 6.5, 6.50",
        "ar; zero: 0, 0.0, 0.00; one: 1, 1.0, 1.00; two: 2, 2.0, 2.00; few: 103, 103.0, 103.00, 110, 303, 310; many: 111, 199, 311, 311.0, 311.00, 399; other: 0.1, 0.303, 1.303, 1.99, 2.303, 100, 100.0, 100.00, 100.303, 101, 103.399, 311.303",
    };

    private <T extends Serializable> T serializeAndDeserialize(T original, Output<Integer> size) { 
        try { 
            ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
            ObjectOutputStream ostream = new ObjectOutputStream(baos); 
            ostream.writeObject(original); 
            ostream.flush(); 
            byte bytes[] = baos.toByteArray();
            size.value = bytes.length;
            ObjectInputStream istream = new ObjectInputStream(new ByteArrayInputStream(bytes)); 
            T reconstituted = (T)istream.readObject(); 
            return reconstituted; 
        } catch(IOException e) { 
            throw new RuntimeException(e); 
        } catch (ClassNotFoundException e) { 
            throw new RuntimeException(e); 
        } 
    } 

    public void TestSerialization() { 
        Output<Integer> size = new Output<Integer>();
        int max = 0;
        for (ULocale locale : PluralRules.getAvailableULocales()) {
            PluralRules item = PluralRules.forLocale(locale); 
            PluralRules item2 = serializeAndDeserialize(item, size); 
            logln(locale + "\tsize:\t" + size.value);
            max = Math.max(max, size.value);
            if (!assertEquals(locale + "\tPlural rules before and after serialization", item, item2)) {
                // for debugging
                PluralRules item3 = serializeAndDeserialize(item, size); 
                item.equals(item3);
            }
        }
        logln("max \tsize:\t" + max);
    }
    
    public static class FixedDecimalHandler implements SerializableTest.Handler
    {
        public Object[] getTestObjects()
        {
            FixedDecimal items[] = {
                    new FixedDecimal(3d),
                    new FixedDecimal(3d, 2),
                    new FixedDecimal(3.1d, 1),
                    new FixedDecimal(3.1d, 2),
            };
            return items;
        }

        public boolean hasSameBehavior(Object a, Object b)
        {
            FixedDecimal a1 = (FixedDecimal) a;
            FixedDecimal b1 = (FixedDecimal) b;
            return a1.equals(b1);
        }
    }

    public void TestSerial() {
        PluralRules s = PluralRules.forLocale(ULocale.ENGLISH);
        checkStreamingEquality(s);
    }

    public void checkStreamingEquality(PluralRules s) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOut);
            objectOutputStream.writeObject(s);
            objectOutputStream.close();
            byte[] contents = byteOut.toByteArray();
            logln(s.getClass() + ": " + showBytes(contents));
            ByteArrayInputStream byteIn = new ByteArrayInputStream(contents);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteIn);
            Object obj = objectInputStream.readObject();
            assertEquals("Streamed Object equals ", s, obj);
        } catch (Exception e) {
            assertNull("TestSerial", e);
        }
    }

    /**
     * @param contents
     * @return
     */
    private String showBytes(byte[] contents) {
        StringBuilder b = new StringBuilder('[');
        for (int i = 0; i < contents.length; ++i) {
            int item = contents[i] & 0xFF;
            if (item >= 0x20 && item <= 0x7F) {
                b.append((char) item);
            } else {
                b.append('(').append(Utility.hex(item, 2)).append(')');
            }
        }
        return b.append(']').toString();
    }
}
