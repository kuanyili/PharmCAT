package org.pharmgkb.pharmcat.reporter.handlebars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link ReportHelpers}.
 *
 * @author Mark Woon
 */
class ReportHelpersTest {


  @Test
  void testSanitizeCssSelector() {
    assertEquals("ABC", ReportHelpers.sanitizeCssSelector("__ABC__"));
    assertEquals("ABC", ReportHelpers.sanitizeCssSelector("(ABC)!"));
    assertEquals("A_B_C", ReportHelpers.sanitizeCssSelector("A B - C"));
  }
}
