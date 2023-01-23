package org.pharmgkb.pharmcat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.TestInfo;
import org.pharmgkb.pharmcat.reporter.ReportContext;
import org.pharmgkb.pharmcat.reporter.model.DataSource;
import org.pharmgkb.pharmcat.reporter.model.MessageAnnotation;
import org.pharmgkb.pharmcat.reporter.model.result.Diplotype;
import org.pharmgkb.pharmcat.reporter.model.result.DrugReport;
import org.pharmgkb.pharmcat.reporter.model.result.GeneReport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;


/**
 * This class wraps {@link Pipeline} to simplify usage in tests.
 *
 * @author Mark Woon
 */
class PipelineWrapper {
  // controls to support running PipelineTest from SyntheticBatchTest
  private static boolean m_compact = true;
  private static List<DataSource> m_sources = Lists.newArrayList(DataSource.CPIC, DataSource.DPWG);

  private final Path m_outputPath;
  private final TestVcfBuilder m_vcfBuilder;
  private final boolean m_findCombinations;
  private final boolean m_callCyp2d6;
  private final boolean m_topCandidatesOnly;
  private boolean m_compactReport = m_compact;
  private ReportContext m_reportContext;


  static void setCompact(boolean compact) {
    m_compact = compact;
  }

  static void setSources(List<DataSource> sources) {
    m_sources = sources;
  }


  PipelineWrapper(TestInfo testInfo, boolean allMatches) throws IOException {
    this(testInfo, false, allMatches, false);
  }

  PipelineWrapper(TestInfo testInfo, boolean findCombinations, boolean allMatches, boolean callCyp2d6)
      throws IOException {
    Preconditions.checkNotNull(testInfo);

    m_outputPath = TestUtils.getTestOutputDir(testInfo, false);
    if (!Files.isDirectory(m_outputPath)) {
      Files.createDirectories(m_outputPath);
    }
    m_vcfBuilder = new TestVcfBuilder(testInfo).saveFile();
    m_findCombinations = findCombinations;
    m_callCyp2d6 = callCyp2d6;
    m_topCandidatesOnly = !allMatches;
  }

  void setCompactReport(boolean compactReport) {
    m_compactReport = compactReport;
  }

  ReportContext getContext() {
    return m_reportContext;
  }

  TestVcfBuilder getVcfBuilder() {
    return m_vcfBuilder;
  }

  Path execute(Path outsideCallPath) throws Exception {
    Path vcfFile = m_vcfBuilder.generate();
    Pipeline pcat = new Pipeline(new Env(),
        true, new VcfFile(vcfFile, false), null, true,
        m_topCandidatesOnly, m_callCyp2d6, m_findCombinations, true,
        true, null, outsideCallPath,
        true, null, null, m_sources, m_compactReport, true,
        m_outputPath, null, m_compactReport,
        Pipeline.Mode.TEST, null, false
    );
    pcat.call();
    m_reportContext = pcat.getReportContext();
    return vcfFile;
  }


  void testMatcher(String gene, String... diplotypes) {
    GeneReport geneReport = getContext().getGeneReport(DataSource.CPIC, gene);
    List<String> dips = geneReport.getSourceDiplotypes().stream()
        .map(Diplotype::printBare)
        .sorted()
        .toList();
    try {
      assertThat(dips, contains(diplotypes));
    } catch (AssertionError ex) {
      System.out.println(printDiagnostic(geneReport));
      throw ex;
    }
  }

  /**
   * Test the "print" calls for a gene that will display in the final report or in the phenotyper. This will check
   * that the call count matches and then check each individual call is present (can be 1 or more).
   *
   * @param gene the gene to get diplotypes for
   * @param calls the expected display of the calls, 1 or more
   */
  void testPrintCpicCalls(String gene, String... calls) {
    testPrintCalls(DataSource.CPIC, gene, calls);
  }

  void testPrintCalls(DataSource source, String gene, String... calls) {
    GeneReport geneReport = getContext().getGeneReport(source, gene);
    SortedSet<String> dips = new TreeSet<>(geneReport.printDisplayCalls());
    Arrays.sort(calls);
    try {
      assertThat(dips, contains(calls));
    } catch (AssertionError ex) {
      System.out.println(printDiagnostic(geneReport));
      throw ex;
    }
  }

  /**
   * Test to make sure the given phenotype is in the collection of phenotyps that come from the source diplotype
   * collection.
   *
   * @param source The source for the diplotype
   * @param gene The gene symbol
   * @param phenotype The phenotype string to check for
   */
  void testSourcePhenotype(DataSource source, String gene, String phenotype) {
    GeneReport geneReport = getContext().getGeneReport(source, gene);
    Set<String> sourcePhenotypes = geneReport.getSourceDiplotypes().stream()
        .flatMap(d -> d.getPhenotypes().stream())
        .collect(Collectors.toSet());
    assertTrue(sourcePhenotypes.contains(phenotype), sourcePhenotypes + " does not contain " + phenotype);
  }

  void testInferredCalls(String gene, String... calls) {
    GeneReport geneReport = getContext().getGeneReport(DataSource.CPIC, gene);
    SortedSet<String> dips = new TreeSet<>(geneReport.printDisplayInferredCalls());
    Arrays.sort(calls);
    try {
      assertThat(dips, contains(calls));
    } catch (AssertionError ex) {
      System.out.println(printDiagnostic(geneReport));
      throw ex;
    }
  }

  /**
   * Test the diplotype that will be used for looking up the recommendation. This will mostly match what's printed in
   * displays but will differ for particular genes.
   *
   * @param gene the gene to get diplotypes for
   * @param haplotypes the expected haplotypes names used for calling, specifying one will assume homozygous,
   * otherwise specify two haplotype names
   */
  void testLookup(String gene, String... haplotypes) {
    Preconditions.checkArgument(haplotypes.length >= 1 && haplotypes.length <= 2,
        "Can only test on 1 or 2 haplotypes");

    Map<String, Integer> lookup = new HashMap<>();
    if (haplotypes.length == 2) {
      if (haplotypes[0].equals(haplotypes[1])) {
        lookup.put(haplotypes[0], 2);
      } else {
        lookup.put(haplotypes[0], 1);
        lookup.put(haplotypes[1], 1);
      }
    } else {
      lookup.put(haplotypes[0], 1);
    }


    GeneReport geneReport = getContext().getGeneReport(DataSource.CPIC, gene);
    assertTrue(geneReport.isReportable(), "Not reportable: " + geneReport.getRecommendationDiplotypes());

    assertTrue(geneReport.getRecommendationDiplotypes().stream()
            .anyMatch(d -> d.computeLookupMap().equals(lookup)),
        "Lookup key " + lookup + " not found in lookup " +
            geneReport.getRecommendationDiplotypes().stream().map(Diplotype::computeLookupMap).toList());
  }

  void testLookupByActivity(String gene, String activityScore) {
    GeneReport geneReport = getContext().getGeneReport(DataSource.CPIC, gene);
    assertTrue(geneReport.isReportable());
    String foundScores = geneReport.getRecommendationDiplotypes().stream()
        .map(Diplotype::getActivityScore)
        .collect(Collectors.joining("; "));
    assertTrue(geneReport.getRecommendationDiplotypes().stream()
        .allMatch(d -> d.printLookupKeys().equals(activityScore)), "Activity score mismatch, expected " + activityScore + " but got " + foundScores);
  }

  /**
   * Check to see if all the given genes have been called by the matcher
   */
  void testCalledByMatcher(String... genes) {
    assertTrue(genes != null && genes.length > 0);
    Arrays.stream(genes)
        .forEach(g -> {
          assertTrue(getContext().getGeneReports(g).stream().allMatch(GeneReport::isCalled) &&
                  getContext().getGeneReports(g).stream().noneMatch(GeneReport::isOutsideCall),
              g + " is not called");
        });
  }

  void testReportable(String... genes) {
    assertTrue(genes != null && genes.length > 0);
    Arrays.stream(genes)
        .forEach(g -> {
          assertTrue(getContext().getGeneReports(g).stream().anyMatch(GeneReport::isReportable),
              g + " is not reportable");
        });
  }

  /**
   * Check to see if none of the given genes have been called by the matcher
   */
  void testNotCalledByMatcher(String... genes) {
    Preconditions.checkArgument(genes != null && genes.length > 0);
    Arrays.stream(genes)
        .forEach(g -> {
          assertTrue(getContext().getGeneReports(g).stream().allMatch(GeneReport::isOutsideCall) ||
                  getContext().getGeneReports(g).stream().noneMatch(GeneReport::isCalled),
              g + " is called");
        });
  }

  /**
   * Check to see if there is a matching recommendation for the given drug name.
   *
   * @param drugName a drug name that has recommendations
   * @param expectedCount the number of matching recommendations you expect
   */
  void testMatchedAnnotations(String drugName, int expectedCount) {
    List<DrugReport> drugReports = getContext().getDrugReports(drugName);
    int numMatched = drugReports.stream()
        .mapToInt(DrugReport::getMatchedAnnotationCount)
        .sum();
    assertEquals(expectedCount, numMatched,
        drugName + " has " + numMatched + " matching recommendation(s) instead of " + expectedCount);
  }

  void testMatchedAnnotations(String drugName, DataSource source, int expectedCount) {
    DrugReport drugReport = getContext().getDrugReport(source, drugName);
    assertNotNull(drugReport);
    assertEquals(expectedCount, drugReport.getMatchedAnnotationCount(),
        drugName + " has " + drugReport.getMatchedAnnotationCount() + " matching " + source +
            " recommendation(s) instead of " + expectedCount);
  }

  void testAnyMatchFromSource(String drugName, DataSource source) {
    DrugReport drugReport = getContext().getDrugReport(source, drugName);
    assertNotNull(drugReport);
    assertTrue(drugReport.getGuidelines().stream().anyMatch((g) -> g.getSource() == source && g.isMatched()),
        drugName + " does not have matching recommendation from " + source);
  }

  void testNoMatchFromSource(String drugName, DataSource source) {
    DrugReport drugReport = getContext().getDrugReport(source, drugName);
    if (drugReport != null) {
      assertTrue(drugReport.getGuidelines().stream().noneMatch(r -> r.getSource() == source && r.isMatched()),
          drugName + " has a matching recommendation from " + source + " and expected none");
    }
  }

  void testMessageCountForDrug(DataSource source, String drugName, int messageCount) {
    DrugReport drugReport = getContext().getDrugReport(source, drugName);
    assertNotNull(drugReport);
    assertEquals(messageCount, drugReport.getMessages().size(),
        drugName + " expected " + messageCount + " messages and got " + drugReport.getMessages());
  }

  void testMessageCountForGene(DataSource source, String geneName, int messageCount) {
    GeneReport report = getContext().getGeneReport(source, geneName);
    assertNotNull(report);
    assertEquals(messageCount, report.getMessages().size(),
        geneName + " expected " + messageCount + " messages and got " + report.getMessages());
  }

  void testGeneHasMessage(DataSource source, String geneName, String... msgNames) {
    GeneReport report = getContext().getGeneReport(source, geneName);
    assertNotNull(report);
    assertTrue(report.getMessages().size() >= msgNames.length);
    List<String> names = report.getMessages().stream()
        .map(MessageAnnotation::getName)
        .toList();
    for (String msgName : msgNames) {
      assertTrue(names.contains(msgName),
          geneName + " is missing expected message with name \"" + msgName + "\" (got " + names + ")");
    }
  }


  private String printDiagnostic(GeneReport geneReport) {
    return String.format("\nMatcher: %s\nReporter: %s\nPrint (displayCalls): %s",
        geneReport.getSourceDiplotypes().toString(),
        geneReport.getRecommendationDiplotypes().toString(),
        geneReport.printDisplayCalls()
    );
  }
}
