package io.apicurio.registry.utils.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class ValidatorsTest {

    @Test
    public void testRFC3986URI() {

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Validators.isValidRFC3986URI(null);
        }, "URIs should not be null");

        Assertions.assertTrue(Validators.isValidRFC3986URI("http://a/b/c/d;p?q"));

        // 5.4.1.  Normal Examples https://tools.ietf.org/html/rfc3986#section-5.4.1

        Assertions.assertTrue(Validators.isValidRFC3986URI("g:h"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("./g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g/"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("/g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("//g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("?y"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g?y"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("#s"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g#s"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g?y#s"));
        Assertions.assertTrue(Validators.isValidRFC3986URI(";x"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g;x"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g;x?y#s"));
        Assertions.assertTrue(Validators.isValidRFC3986URI(""));
        Assertions.assertTrue(Validators.isValidRFC3986URI("."));
        Assertions.assertTrue(Validators.isValidRFC3986URI("./"));
        Assertions.assertTrue(Validators.isValidRFC3986URI(".."));
        Assertions.assertTrue(Validators.isValidRFC3986URI("../"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("../g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("../.."));
        Assertions.assertTrue(Validators.isValidRFC3986URI("../../"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("../../g"));

        // 5.4.2.  Abnormal Examples https://tools.ietf.org/html/rfc3986#section-5.4.2

        Assertions.assertTrue(Validators.isValidRFC3986URI("../../../g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("../../../../g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("/./g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("/../g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g."));
        Assertions.assertTrue(Validators.isValidRFC3986URI(".g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g.."));
        Assertions.assertTrue(Validators.isValidRFC3986URI("..g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("./../g"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("./g/."));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g/./h"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g/../h"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g;x=1/./y"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g;x=1/../y"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g?y/./x"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g?y/../x"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g#s/./x"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("g#s/../x"));
        Assertions.assertTrue(Validators.isValidRFC3986URI("http:g"));

    }

}