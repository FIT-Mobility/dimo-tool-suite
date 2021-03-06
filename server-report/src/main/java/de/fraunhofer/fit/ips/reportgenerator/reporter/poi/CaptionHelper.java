package de.fraunhofer.fit.ips.reportgenerator.reporter.poi;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.impl.STFldCharTypeImpl;

/**
 * @author Fabian Ohler <fabian.ohler1@rwth-aachen.de>
 */
@RequiredArgsConstructor
public class CaptionHelper {
    final CursorHelper cursorHelper;
    final BookmarkRegistry bookmarkRegistry;

    public void createTableCaption(final String stringBeforeConceptWithTrailingSpace,
                                   final BookmarkHelper bookmarkHelper,
                                   final String stringAfterConceptWithLeadingSpace) {
        createTableCaption(cursorHelper, bookmarkRegistry,
                stringBeforeConceptWithTrailingSpace, bookmarkHelper, stringAfterConceptWithLeadingSpace);
    }

    public void createFigureCaption(final String stringBeforeConceptWithTrailingSpace,
                                    final BookmarkHelper bookmarkHelper,
                                    final String stringAfterConceptWithLeadingSpace) {
        createFigureCaption(cursorHelper, bookmarkRegistry,
                stringBeforeConceptWithTrailingSpace, bookmarkHelper, stringAfterConceptWithLeadingSpace);
    }

    public void createAssertionCaption(final String stringBeforeConceptWithTrailingSpace,
                                       final BookmarkHelper bookmarkHelper,
                                       final String stringAfterConceptWithLeadingSpace) {
        createAssertionCaption(cursorHelper, bookmarkRegistry,
                stringBeforeConceptWithTrailingSpace, bookmarkHelper, stringAfterConceptWithLeadingSpace);
    }

    public static void createTableCaption(final CursorHelper cursorHelper, final BookmarkRegistry bookmarkRegistry,
                                          final String stringBeforeConceptWithTrailingSpace,
                                          final BookmarkHelper bookmarkHelper,
                                          final String stringAfterConceptWithLeadingSpace) {
        createFloatCaption(cursorHelper, bookmarkRegistry,
                stringBeforeConceptWithTrailingSpace, bookmarkHelper, stringAfterConceptWithLeadingSpace,
                "Table ", "Tabelle", VdvStyle.TABELLEBERSCHRIFT);
    }

    public static void createFigureCaption(final CursorHelper cursorHelper, final BookmarkRegistry bookmarkRegistry,
                                           final String stringBeforeConceptWithTrailingSpace,
                                           final BookmarkHelper bookmarkHelper,
                                           final String stringAfterConceptWithLeadingSpace) {
        createFloatCaption(cursorHelper, bookmarkRegistry,
                stringBeforeConceptWithTrailingSpace, bookmarkHelper, stringAfterConceptWithLeadingSpace,
                "Figure ", "Abbildung", VdvStyle.GRAFIKTITEL);
    }

    public static void createAssertionCaption(final CursorHelper cursorHelper, final BookmarkRegistry bookmarkRegistry,
                                              final String stringBeforeConceptWithTrailingSpace,
                                              final BookmarkHelper bookmarkHelper,
                                              final String stringAfterConceptWithLeadingSpace) {
        createFloatCaption(cursorHelper, bookmarkRegistry,
                stringBeforeConceptWithTrailingSpace, bookmarkHelper, stringAfterConceptWithLeadingSpace,
                "Assertion ", "Assertion", VdvStyle.CAPTION);
    }

    public static void createFloatCaption(final CursorHelper cursorHelper,
                                          final BookmarkRegistry bookmarkRegistry,
                                          final String stringBeforeConceptWithTrailingSpace,
                                          final BookmarkHelper bookmarkHelper,
                                          final String stringAfterConceptWithLeadingSpace,
                                          final String floatTypeNameWithTrailingSpace,
                                          final String sequenceName,
                                          final VdvStyle style) {
        final ParagraphHelper.RunHelper runHelper = new ParagraphHelper(cursorHelper).createRunHelper(style);
        try (final BookmarkRegistry.Helper ignored = bookmarkRegistry.createBookmark(runHelper.paragraph, bookmarkHelper.toFloatLabel())) {
            runHelper.text(floatTypeNameWithTrailingSpace);
            runHelper.paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharTypeImpl.BEGIN);
            {
                final CTText instrText = runHelper.paragraph.createRun().getCTR().addNewInstrText();
                instrText.setSpace(SpaceAttribute.Space.PRESERVE);
                instrText.setStringValue("SEQ " + sequenceName + " \\* ARABIC ");
            }
            runHelper.paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharTypeImpl.SEPARATE);
            runHelper.text("refresh-me");
            runHelper.paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharTypeImpl.END);
        }
        runHelper.text(": " + stringBeforeConceptWithTrailingSpace);
        try (final BookmarkRegistry.Helper ignored = bookmarkRegistry.createBookmark(runHelper.paragraph, bookmarkHelper.toConceptLabel())) {
            runHelper.text(bookmarkHelper.getOriginalName());
        }
        runHelper.text(stringAfterConceptWithLeadingSpace);
    }
}
