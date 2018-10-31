package de.fraunhofer.fit.omp.reportgenerator.reporter.xsd.poi;

import de.fraunhofer.fit.omp.reportgenerator.model.template.Function;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.AttributeVisitor;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Attributes;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Choice;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Derivation;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Documentations;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Element;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.ElementList;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.GroupRef;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.NamedConceptWithOrigin;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.NamedConceptWithOriginVisitor;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Origin;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Schema;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Sequence;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.SequenceOrChoice;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.SequenceOrChoiceOrGroupRef;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.SequenceOrChoiceOrGroupRefOrElementList;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.SequenceOrChoiceOrGroupRefOrElementListVisitor;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.SequenceOrChoiceOrGroupRefVisitor;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.SequenceOrChoiceVisitor;
import de.fraunhofer.fit.omp.reportgenerator.model.xsd.Type;
import de.fraunhofer.fit.omp.reportgenerator.reporter.Config;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import static de.fraunhofer.fit.omp.reportgenerator.reporter.xsd.poi.Constants.getPrefixedName;

/**
 * @author Fabian Ohler <fabian.ohler1@rwth-aachen.de>
 */
@Slf4j
public class VdvTables {

    public static void handleFunctionWithoutHeading(final Context context,
                                                    final CursorHelper cursorHelper,
                                                    final Function operation) {
        final ParagraphHelper paragraphHelper = new ParagraphHelper(cursorHelper);
        final CaptionHelper captionHelper = new CaptionHelper(cursorHelper, context.bookmarkRegistry);

        final Type.Complex inputDataType = operation.getInputDataType();
        final Type.Complex outputDataType = operation.getOutputDataType();

        final boolean noOutput = null == outputDataType;
        final boolean noInput = null == inputDataType;

        if (noInput && noOutput) {
            return;
        }

        if (noOutput) {
            handleFunctionWithoutHeadingHelper(context, cursorHelper, paragraphHelper, captionHelper, inputDataType, "Input");
            return;
        }

        if (noInput) {
            handleFunctionWithoutHeadingHelper(context, cursorHelper, paragraphHelper, captionHelper, outputDataType, "Output");
            return;
        }

        handleFunctionWithoutHeadingHelper(context, cursorHelper, paragraphHelper, captionHelper, inputDataType, "Request");
        handleFunctionWithoutHeadingHelper(context, cursorHelper, paragraphHelper, captionHelper, outputDataType, "Response");
    }

    private static void handleFunctionWithoutHeadingHelper(final Context context,
                                                           final CursorHelper cursorHelper,
                                                           final ParagraphHelper paragraphHelper,
                                                           final CaptionHelper captionHelper,
                                                           final Type.Complex dataType,
                                                           final String headingText) {
        paragraphHelper.createHeading(VdvStyle.HEADING_4, headingText);
        final QName dataTypeName = dataType.getName();
        if (!context.dependencyHelper.getLocalConceptNames().contains(dataTypeName)) {
            paragraphHelper.createRunHelper(VdvStyle.NORMAL)
                           .text("The data type '" + getPrefixedName(context.schema, dataTypeName) + "' is defined in an imported/included xsd.");
            return;
        }
        if (context.dependencyHelper.getCommonConceptNames().contains(dataTypeName)) {
            paragraphHelper.createRunHelper(VdvStyle.NORMAL)
                           .text("The data type '" + getPrefixedName(context.schema, dataTypeName) + "' is defined in the common types section, see ")
                           .bookmarkRef(new BookmarkHelper(dataTypeName.getLocalPart()), BookmarkHelper::toFloatLabel)
                           .text(".");
            return;
        }
        processDataTypeAndDependencies(context, cursorHelper, captionHelper, dataType);
    }

    public static void processDataTypeAndDependencies(final Context context,
                                                      final CursorHelper cursorHelper,
                                                      final CaptionHelper captionHelper,
                                                      final Type.Complex dataType) {
        if (null == dataType) {
            return;
        }
        processComplexDataType(context, cursorHelper, captionHelper, dataType);
        SimpleTypeTableBookmarker bookmarker = SimpleTypeTableBookmarker.localToType(dataType.getName().getLocalPart());

        try (final Processor processor = new Processor(context, cursorHelper, captionHelper, bookmarker)) {
            LinkedHashSet<QName> dependencyNames = context.dependencyHelper.getLocalRRTypeToDependencies().get(dataType.getName());
            if (dependencyNames == null) {
                return;
            }
            for (final QName dependency : dependencyNames) {
                processCorrectly(context, processor, dependency);
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SimpleTypeTableBookmarker {
        final String stringBeforeConceptWithTrailingSpace;
        final BookmarkHelper bookmarkHelperSimpleTypes;
        final BookmarkHelper bookmarkHelperEnumerations;
        final BookmarkHelper bookmarkHelperAttributes;
        final String stringAfterConceptWithLeadingSpace;

        public static SimpleTypeTableBookmarker common() {
            return new SimpleTypeTableBookmarker(
                    "Description of ",
                    new BookmarkHelper("common simple types"),
                    new BookmarkHelper("common enumerations"),
                    new BookmarkHelper("common attributes"),
                    "");
        }

        public static SimpleTypeTableBookmarker localToType(@Nonnull final String typeName) {
            return new SimpleTypeTableBookmarker(
                    "Description of ",
                    new BookmarkHelper("common simple types local to " + typeName),
                    new BookmarkHelper("common enumerations local to " + typeName),
                    new BookmarkHelper("common attributes local to " + typeName),
                    "");
        }
    }

    @RequiredArgsConstructor
    public static class Processor implements AutoCloseable, NamedConceptWithOriginVisitor {
        private final Context context;
        private final CursorHelper cursorHelper;
        private final CaptionHelper captionHelper;

        private final SimpleTypeTableBookmarker simpleTypeTableBookmarker;
        private final LinkedList<Type.Simple.Restriction> restrictions = new LinkedList<>();
        private final LinkedList<Type.Simple.Enumeration> enumerations = new LinkedList<>();
        private final LinkedList<Type.Simple.Union> unions = new LinkedList<>();
        private final LinkedList<Type.Simple.List> lists = new LinkedList<>();
        private final LinkedList<Attributes.GlobalAttributeDeclaration> attributes = new LinkedList<>();

        private static final Comparator<Type.Simple> SIMPLE_TYPE_COMPARATOR = Comparator.comparing(Type.Simple::getName, Comparator.comparing(QName::getLocalPart));

        @Override
        public void visit(final Element element) {
            // TODO impl top level elements table
        }

        @Override
        public void visit(final Type.Group group) {
            processGroupDataType(context, cursorHelper, captionHelper, group);
        }

        @Override
        public void visit(final Type.Complex complex) {
            processComplexDataType(context, cursorHelper, captionHelper, complex);
        }

        @Override
        public void visit(final Type.Simple.Restriction restriction) {
            restrictions.add(restriction);
        }

        @Override
        public void visit(final Type.Simple.Union union) {
            unions.add(union);
        }

        @Override
        public void visit(final Type.Simple.List list) {
            lists.add(list);
        }

        @Override
        public void visit(final Type.Simple.Enumeration enumeration) {
            enumerations.add(enumeration);
        }

        @Override
        public void visit(final Attributes.GlobalAttributeGroupDeclaration globalAttributeGroupDeclaration) {
            // TODO impl top level attribute groups table
        }

        @Override
        public void visit(final Attributes.GlobalAttributeDeclaration globalAttributeDeclaration) {
            attributes.add(globalAttributeDeclaration);
        }

        @Override
        public void close() {
            restrictions.sort(SIMPLE_TYPE_COMPARATOR);
            lists.sort(SIMPLE_TYPE_COMPARATOR);
            unions.sort(SIMPLE_TYPE_COMPARATOR);
            enumerations.sort(SIMPLE_TYPE_COMPARATOR);
            if (!(restrictions.isEmpty() && lists.isEmpty() && unions.isEmpty())) {
                captionHelper.createTableCaption(
                        simpleTypeTableBookmarker.stringBeforeConceptWithTrailingSpace,
                        simpleTypeTableBookmarker.bookmarkHelperSimpleTypes,
                        simpleTypeTableBookmarker.stringAfterConceptWithLeadingSpace
                );
                final SimpleTypesTableHelper dataTypeTableHelper = new SimpleTypesTableHelper(context, cursorHelper);
                for (final Type.Simple.Restriction simpleType : restrictions) {
                    dataTypeTableHelper.addRestriction(simpleType);
                }
                for (final Type.Simple.Union simpleType : unions) {
                    dataTypeTableHelper.addUnion(simpleType);
                }
                for (final Type.Simple.List simpleType : lists) {
                    dataTypeTableHelper.addList(simpleType);
                }
            }
            for (final Type.Simple.Enumeration enumeration : enumerations) {
                processEnumeration(context, cursorHelper, captionHelper, enumeration);
            }
            // FIXME print top level elements
            // FIXME print (missing?) top level groups
            if (!attributes.isEmpty()) {
                captionHelper.createTableCaption(
                        simpleTypeTableBookmarker.stringBeforeConceptWithTrailingSpace,
                        simpleTypeTableBookmarker.bookmarkHelperAttributes,
                        simpleTypeTableBookmarker.stringAfterConceptWithLeadingSpace
                );
                final AttributesTableHelper attributesTableHelper = new AttributesTableHelper(context, cursorHelper);
                for (final Attributes.GlobalAttributeDeclaration attribute : attributes) {
                    attributesTableHelper.addAttributeDeclaration(attribute);
                }
            }
            // FIXME print top level attribute groups
        }
    }


    public static void processCorrectly(final Context context,
                                        final Processor processor,
                                        final QName conceptName) {
        context.getConcept(conceptName).accept(processor);
    }

    public static void processGroupDataType(final Context context,
                                            final CursorHelper cursorHelper,
                                            final CaptionHelper captionHelper,
                                            final Type.Group dataType) {
        captionHelper.createTableCaption(
                "Description of ",
                new BookmarkHelper(dataType.getName().getLocalPart()),
                ""
        );
        final DataTypeTableHelper dataTypeTableHelper = new DataTypeTableHelper(
                context,
                cursorHelper,
                dataType.getName(),
                Constants.getDocs(dataType.getDocs())
        );
        final SequenceOrChoiceOrGroupRef particle = dataType.getParticle();
        particle.accept(new ComplexTypeVisitor(dataTypeTableHelper, context.schema));
    }

    public static void processComplexDataType(final Context context,
                                              final CursorHelper cursorHelper,
                                              final CaptionHelper captionHelper,
                                              final Type.Complex dataType) {
        captionHelper.createTableCaption(
                "Description of ",
                new BookmarkHelper(dataType.getName().getLocalPart()),
                ""
        );
        final DataTypeTableHelper dataTypeTableHelper = new DataTypeTableHelper(
                context,
                cursorHelper,
                dataType.getName(),
                Constants.getDocs(dataType.getDocs())
        );
        final Derivation derivation = dataType.getDerivation();
        switch (derivation.getType()) {
            case EXTENSION:
                if (Config.HIDE_INHERITANCE_IN_EXTENSIONS) {
                    dataTypeTableHelper.addExtensionRow(derivation.getBaseName());
                } else {
                    final QName baseName = derivation.getBaseName();
                    final NamedConceptWithOrigin base = context.getConcept(baseName);
                    base.accept(new InheritancePrinter(context, dataTypeTableHelper));
                }
                break;
            case RESTRICTION:
                dataTypeTableHelper.addRestrictionRow(derivation.getBaseName());
                break;
        }
        final SequenceOrChoiceOrGroupRef particle = dataType.getParticle();
        particle.accept(new ComplexTypeVisitor(dataTypeTableHelper, context.schema));

        final Attributes attributes = dataType.getAttributes();
        OuterAttributeVisitor.handle(dataTypeTableHelper, context, attributes);
    }

    @RequiredArgsConstructor
    public static class InheritancePrinter implements NamedConceptWithOriginVisitor {
        final Context context;
        final DataTypeTableHelper tableHelper;

        // follow the type hierarchy up until reaching the first restriction (recursively)
        // on the way back of the recursion, print all the levels including the first restriction
        @Override
        public void visit(final Type.Complex complex) {
            final Derivation derivation = complex.getDerivation();
            final QName baseName = derivation.getBaseName();
            // base might be null in case of dummy restriction
            @Nullable final NamedConceptWithOrigin base = context.getConcept(baseName);
            // recurse in case of extension
            switch (derivation.getType()) {
                case EXTENSION: {
                    assert base != null;
                    base.accept(this);
                    break;
                }
                case RESTRICTION:
                case NONE:
                    break;
            }
            // we are on our way back up in the recursion hierarchy, now print the content
            try (final DataTypeTableHelper.GroupHelper groupHelper = tableHelper.startGroup(complex.getName())) {
                if (Derivation.Type.RESTRICTION == derivation.getType()) {
                    // only print restriction line if we don't restrict xs:anyType
                    groupHelper.addRestrictionRow(baseName);
                }
                complex.getParticle().accept(new SequenceOrChoiceOrGroupRefOrElementListVisitor() {
                    @Override
                    public void visit(final Sequence sequence) {
                        final SequenceInGroupRefVisitor visitor = new SequenceInGroupRefVisitor(groupHelper);
                        for (final SequenceOrChoiceOrGroupRefOrElementList child : sequence.getParticleList()) {
                            child.accept(visitor);
                        }
                    }

                    @Override
                    public void visit(final Choice choice) {
                        try (final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper = groupHelper.startChoice(choice.getCardinality())) {
                            final ChoiceInGroupRefVisitor visitor = new ChoiceInGroupRefVisitor(choiceHelper);
                            for (final SequenceOrChoiceOrGroupRefOrElementList child : choice.getParticleList()) {
                                child.accept(visitor);
                            }
                        }
                    }

                    @Override
                    public void visit(final GroupRef groupRef) {
                        groupHelper.addElement(
                                groupRef.getCardinality(),
                                groupRef.getRefName(),
                                groupRef.getRefName(),
                                Constants.getDocs(groupRef.getDocs())
                        );
                    }

                    @Override
                    public void visit(final ElementList elementList) {
                        log.error("element list at top level of complex type");
                    }
                });

                new InnerAttributeVisitorImpl(groupHelper, context).handle(complex.getAttributes());
            }
        }

        @Override
        public void visit(final Type.Simple.Restriction restriction) {
            // hit first restriction, don't recurse, print the content
            // FIXME integrate simpleContent for complexType
        }

        @Override
        public void visit(final Type.Simple.Enumeration enumeration) {
            // hit first restriction, don't recurse, print the content
            // FIXME integrate simpleContent for complexType
        }

        @Override
        public void visit(final Type.Simple.List concept) {
            // hit first restriction, don't recurse, print the content
            // FIXME integrate simpleContent for complexType
        }

        @Override
        public void visit(final Type.Simple.Union concept) {
            // hit first restriction, don't recurse, print the content
            // FIXME integrate simpleContent for complexType
        }

        @Override
        public void visit(Element concept) {
            throw new IllegalArgumentException("found " + concept + "in InheritancePrinter!");
        }

        @Override
        public void visit(Type.Group concept) {
            throw new IllegalArgumentException("found " + concept + "in InheritancePrinter!");
        }

        @Override
        public void visit(Attributes.GlobalAttributeGroupDeclaration concept) {
            throw new IllegalArgumentException("found " + concept + "in InheritancePrinter!");
        }

        @Override
        public void visit(Attributes.GlobalAttributeDeclaration concept) {
            throw new IllegalArgumentException("found " + concept + "in InheritancePrinter!");
        }
    }

    public static void processEnumeration(final Context context,
                                          final CursorHelper cursorHelper,
                                          final CaptionHelper captionHelper,
                                          final Type.Simple.Enumeration dataType) {
        captionHelper.createTableCaption(
                "Description of Enumeration ",
                new BookmarkHelper(dataType.getName().getLocalPart()),
                ""
        );
        final EnumerationTableHelper enumTableHelper = new EnumerationTableHelper(
                context,
                cursorHelper,
                dataType.getName().getLocalPart(),
                dataType.getBaseType(),
                Constants.getDocs(dataType.getDocs())
        );
        for (final Type.Simple.Enumeration.Value value : dataType.getEnumValues()) {
            enumTableHelper.addValue(value.getValue(), Constants.getDocs(value.getDocs()));
        }
    }

    @RequiredArgsConstructor
    static class ComplexTypeVisitor implements SequenceOrChoiceOrGroupRefVisitor {
        final DataTypeTableHelper dataTypeTableHelper;
        final Schema schema;

        @Override
        public void visit(final Sequence sequence) {
            final List<SequenceOrChoiceOrGroupRefOrElementList> children = sequence.getParticleList();
            final SequenceInComplexTypeVisitor visitor = new SequenceInComplexTypeVisitor(dataTypeTableHelper, schema);
            for (final SequenceOrChoiceOrGroupRefOrElementList child : children) {
                child.accept(visitor);
            }
        }

        @Override
        public void visit(final Choice choice) {
            final List<SequenceOrChoiceOrGroupRefOrElementList> children = choice.getParticleList();
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(new QName(""))) {
                try (final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper = groupHelper.startChoice(choice.getCardinality())) {
                    final ChoiceInComplexTypeVisitor visitor = new ChoiceInComplexTypeVisitor(choiceHelper, schema);
                    for (final SequenceOrChoiceOrGroupRefOrElementList child : children) {
                        child.accept(visitor);
                    }
                }
            }
        }

        @Override
        public void visit(final GroupRef groupRef) {
            final QName refName = groupRef.getRefName();
            final Type.Group group = (Type.Group) schema.getConcepts().get(refName);
            final SequenceOrChoice particle = group.getParticle();
            final GroupRefInComplexTypeVisitor visitor = new GroupRefInComplexTypeVisitor(dataTypeTableHelper, refName);
            particle.accept(visitor);
        }

        @Override
        public void visit(final ElementList elementList) {
            log.error("element list at top level of complex type");
        }
    }

    @RequiredArgsConstructor
    static class SequenceInComplexTypeVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper dataTypeTableHelper;
        final Schema schema;

        @Override
        public void visit(final Sequence sequence) {
            final List<SequenceOrChoiceOrGroupRefOrElementList> children = sequence.getParticleList();
            for (final SequenceOrChoiceOrGroupRefOrElementList child : children) {
                child.accept(this);
            }
        }

        @Override
        public void visit(final Choice choice) {
            // anon choice
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(new QName(""))) {
                try (final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper = groupHelper.startChoice(choice.getCardinality())) {
                    final ChoiceInSequenceVisitor visitor = new ChoiceInSequenceVisitor(choiceHelper);
                    for (final SequenceOrChoiceOrGroupRefOrElementList child : choice.getParticleList()) {
                        child.accept(visitor);
                    }
                }
            }
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // kinder hochziehen
            final QName refName = groupRef.getRefName();
            final Type.Group group = (Type.Group) schema.getConcepts().get(refName);
            final SequenceOrChoice particle = group.getParticle();
            particle.accept(new GroupRefInSequenceVisitor(dataTypeTableHelper, refName));
        }

        @Override
        public void visit(final ElementList elementList) {
            // new anon group
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(new QName(""))) {
                for (final Element element : elementList.getElements()) {
                    processGroupElement(groupHelper, element);
                }
            }
        }
    }

    @RequiredArgsConstructor
    static class ChoiceInComplexTypeVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper;
        final Schema schema;

        @Override
        public void visit(final Sequence sequence) {
            // nested sequence
            log.error("sequence nested in choice");
        }

        @Override
        public void visit(final Choice choice) {
            // nested choice
            log.error("choice nested in choice");
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // als Element einbetten
            choiceHelper.addOption(
                    groupRef.getRefName(),
                    groupRef.getRefName(),
                    Constants.getDocs(groupRef.getDocs())
            );
        }

        @Override
        public void visit(final ElementList elementList) {
            // auspacken
            for (final Element element : elementList.getElements()) {
                processChoiceElement(choiceHelper, element);
            }
        }
    }

    @RequiredArgsConstructor
    static class GroupRefInComplexTypeVisitor implements SequenceOrChoiceVisitor {
        final DataTypeTableHelper dataTypeTableHelper;
        final QName groupName;

        @Override
        public void visit(final Sequence sequence) {
            // new named group
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(groupName)) {
                final SequenceInGroupRefVisitor visitor = new SequenceInGroupRefVisitor(groupHelper);
                for (final SequenceOrChoiceOrGroupRefOrElementList child : sequence.getParticleList()) {
                    child.accept(visitor);
                }
            }
        }

        @Override
        public void visit(final Choice choice) {
            // named choice
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(groupName)) {
                try (final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper = groupHelper.startChoice(choice.getCardinality())) {
                    final ChoiceInGroupRefVisitor visitor = new ChoiceInGroupRefVisitor(choiceHelper);
                    for (final SequenceOrChoiceOrGroupRefOrElementList child : choice.getParticleList()) {
                        child.accept(visitor);
                    }
                }
            }
        }
    }

    @RequiredArgsConstructor
    static class ChoiceInSequenceVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper;

        @Override
        public void visit(final Sequence sequence) {
            log.error("sequence nested in choice nested in sequence");
        }

        @Override
        public void visit(final Choice choice) {
            log.error("sequence nested in choice nested in sequence");
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // als element
            choiceHelper.addOption(
                    groupRef.getRefName(),
                    groupRef.getRefName(),
                    Constants.getDocs(groupRef.getDocs())
            );
        }

        @Override
        public void visit(final ElementList elementList) {
            for (final Element element : elementList.getElements()) {
                processChoiceElement(choiceHelper, element);
            }
        }
    }

    @RequiredArgsConstructor
    static class GroupRefInSequenceVisitor implements SequenceOrChoiceVisitor {
        final DataTypeTableHelper dataTypeTableHelper;
        final QName groupName;

        @Override
        public void visit(final Sequence sequence) {
            // group-named sequence
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(groupName)) {
                final SequenceInGroupRefInSequenceVisitor visitor = new SequenceInGroupRefInSequenceVisitor(groupHelper);
                for (final SequenceOrChoiceOrGroupRefOrElementList child : sequence.getParticleList()) {
                    child.accept(visitor);
                }
            }
        }

        @Override
        public void visit(final Choice choice) {
            // name of group conflicts with separation of choice
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(groupName)) {
                try (final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper = groupHelper.startChoice(choice.getCardinality())) {
                    final ChoiceInGroupRefInSequenceVisitor visitor = new ChoiceInGroupRefInSequenceVisitor(choiceHelper);
                    for (final SequenceOrChoiceOrGroupRefOrElementList child : choice.getParticleList()) {
                        child.accept(visitor);
                    }
                }
            }
        }
    }

    @RequiredArgsConstructor
    public static class SequenceInGroupRefInSequenceVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper.GroupHelper groupHelper;

        @Override
        public void visit(final Sequence sequence) {
            log.error("sequence nested in group nested in sequence");
        }

        @Override
        public void visit(final Choice choice) {
            log.error("choice nested in group nested in sequence");
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // print as element
            groupHelper.addElement(
                    groupRef.getCardinality(),
                    groupRef.getRefName(),
                    groupRef.getRefName(),
                    Constants.getDocs(groupRef.getDocs())
            );
        }

        @Override
        public void visit(final ElementList elementList) {
            // print elements
            for (final Element element : elementList.getElements()) {
                processGroupElement(groupHelper, element);
            }
        }
    }

    @RequiredArgsConstructor
    public static class ChoiceInGroupRefInSequenceVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper;

        @Override
        public void visit(final Sequence sequence) {
            log.error("sequence nested in choice nested in group nested in sequence");
        }

        @Override
        public void visit(final Choice choice) {
            log.error("choice nested in choice nested in group nested in sequence");
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // print as element
            choiceHelper.addOption(
                    groupRef.getRefName(),
                    groupRef.getRefName(),
                    Constants.getDocs(groupRef.getDocs())
            );
        }

        @Override
        public void visit(final ElementList elementList) {
            // print elements
            for (final Element element : elementList.getElements()) {
                processChoiceElement(choiceHelper, element);
            }
        }
    }

    @RequiredArgsConstructor
    static class SequenceInGroupRefVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper.GroupHelper groupHelper;

        @Override
        public void visit(final Sequence sequence) {
            log.error("sequence nested in sequence");
        }

        @Override
        public void visit(final Choice choice) {
            try (final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper = groupHelper.startChoice(choice.getCardinality())) {
                final ChoiceInGroupRefVisitor visitor = new ChoiceInGroupRefVisitor(choiceHelper);
                for (final SequenceOrChoiceOrGroupRefOrElementList sequenceOrChoiceOrGroupRefOrElementList : choice.getParticleList()) {
                    sequenceOrChoiceOrGroupRefOrElementList.accept(visitor);
                }
            }
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // add group as element as option
            groupHelper.addElement(groupRef.getCardinality(),
                    groupRef.getRefName(),
                    groupRef.getRefName(),
                    Constants.getDocs(groupRef.getDocs())
            );
        }

        @Override
        public void visit(final ElementList elementList) {
            // add elements as options
            for (final Element element : elementList.getElements()) {
                processGroupElement(groupHelper, element);
            }
        }
    }

    @RequiredArgsConstructor
    static class ChoiceInGroupRefVisitor implements SequenceOrChoiceOrGroupRefOrElementListVisitor {
        final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper;

        @Override
        public void visit(final Sequence sequence) {
            log.error("sequence nested in choice nested in group");
        }

        @Override
        public void visit(final Choice choice) {
            log.error("choice nested in choice nested in group");
        }

        @Override
        public void visit(final GroupRef groupRef) {
            // add group as element as option
            choiceHelper.addOption(groupRef.getRefName(),
                    groupRef.getRefName(),
                    Constants.getDocs(groupRef.getDocs())
            );
        }

        @Override
        public void visit(final ElementList elementList) {
            // add elements as options
            for (final Element element : elementList.getElements()) {
                processChoiceElement(choiceHelper, element);
            }
        }
    }

    public static void processGroupElement(final DataTypeTableHelper.GroupHelper groupHelper,
                                           final Element element) {
        groupHelper.addElement(
                element.getCardinality(),
                element.getName(),
                element.getDataType(),
                Constants.getDocs(element.getDocs())
        );
    }

    public static void processChoiceElement(final DataTypeTableHelper.GroupHelper.ChoiceHelper choiceHelper,
                                            final Element element) {
        choiceHelper.addOption(
                element.getName(),
                element.getDataType(),
                Constants.getDocs(element.getDocs())
        );
    }

    public static void processAttribute(final DataTypeTableHelper.GroupHelper attributesHelper,
                                        final boolean required, final QName attributeName, final QName typeName,
                                        final Attributes.AttributeDefaultOrFixedValue defaultOrFixedValue,
                                        final List<String> description) {
        attributesHelper.addAttribute(required, attributeName, typeName, defaultOrFixedValue, description);
    }

    @RequiredArgsConstructor
    private static class OuterAttributeVisitor implements AttributeVisitor {
        final DataTypeTableHelper dataTypeTableHelper;
        final Context context;
        final boolean noTlWildcard;
        DataTypeTableHelper.GroupHelper groupHelper;

        public static void handle(final DataTypeTableHelper dataTypeTableHelper,
                                  final Context context,
                                  final Attributes attributes) {
            new OuterAttributeVisitor(dataTypeTableHelper, context, null == attributes.getAnyAttribute()).handle(attributes);
        }

        private void handle(final Attributes attributes) {
            for (final Attributes.AttributeOrAttributeGroup attributeOrAttributeGroup : attributes.getAttributes().values()) {
                attributeOrAttributeGroup.accept(this);
            }
            final Attributes.AnyAttribute anyAttribute = attributes.getAnyAttribute();
            if (null != anyAttribute) {
                initGroupHelper();
                groupHelper.addWildcard(anyAttribute);
            }
            deinitGroupHelper();
        }

        private void initGroupHelper() {
            if (null == groupHelper) {
                groupHelper = dataTypeTableHelper.startGroup(new QName(""));
            }
        }

        private void deinitGroupHelper() {
            if (null != groupHelper) {
                groupHelper.close();
                groupHelper = null;
            }
        }

        @Override
        public void visit(final Attributes.LocalAttribute localAttribute) {
            initGroupHelper();

            final boolean required = localAttribute.isRequired();
            final Documentations useDocs = localAttribute.getDocs();
            final Attributes.AttributeDefaultOrFixedValue defaultOrFixedValue = localAttribute.getDefaultOrFixedValue();
            final Attributes.LocalAttributeDeclaration localAttributeDeclaration = localAttribute.getLocalAttributeDeclaration();
            final QName name = localAttributeDeclaration.getName();
            final Documentations declDocs = localAttributeDeclaration.getDocs();
            final QName type = localAttributeDeclaration.getType();

            processAttribute(groupHelper, required, name, type, defaultOrFixedValue, Constants.getDocs(useDocs, declDocs));
        }

        @Override
        public void visit(final Attributes.GlobalAttribute globalAttribute) {
            initGroupHelper();

            final boolean required = globalAttribute.isRequired();
            final Documentations useDocs = globalAttribute.getDocs();
            final Attributes.AttributeDefaultOrFixedValue useDefaultOrFixedValue = globalAttribute.getDefaultOrFixedValue();
            final Attributes.GlobalAttributeDeclaration globalAttributeDeclaration = (Attributes.GlobalAttributeDeclaration) context.getConcept(globalAttribute.getGlobalAttributeDeclarationName());
            final QName name = globalAttributeDeclaration.getName();
            final Documentations declDocs = globalAttributeDeclaration.getDocs();
            final QName type = globalAttributeDeclaration.getType();
            final Origin origin = globalAttributeDeclaration.getOrigin();
            final Attributes.AttributeDefaultOrFixedValue declDefaultOrFixedValue = globalAttributeDeclaration.getDefaultOrFixedValue();

            final Attributes.AttributeDefaultOrFixedValue defaultOrFixedValue =
                    Attributes.AttributeDefaultOrFixedValue.NONE != useDefaultOrFixedValue ?
                            useDefaultOrFixedValue : declDefaultOrFixedValue;

            processAttribute(groupHelper, required, name, type, defaultOrFixedValue, Constants.getDocs(useDocs, declDocs));
        }

        @Override
        public void visit(final Attributes.AttributeGroup attributeGroup) {
            final Documentations useDocs = attributeGroup.getDocs();
            final Attributes.GlobalAttributeGroupDeclaration globalAttributeGroupDeclaration = (Attributes.GlobalAttributeGroupDeclaration) context.getConcept(attributeGroup.getAttributeGroupDeclarationName());
            final QName name = globalAttributeGroupDeclaration.getName();
            final Documentations declDocs = globalAttributeGroupDeclaration.getDocs();
            final Origin origin = globalAttributeGroupDeclaration.getOrigin();
            final LinkedHashMap<QName, Attributes.AttributeOrAttributeGroup> attributes = globalAttributeGroupDeclaration.getAttributes();
            final Attributes.AnyAttribute anyAttribute = globalAttributeGroupDeclaration.getAnyAttribute();

            deinitGroupHelper();
            try (final DataTypeTableHelper.GroupHelper groupHelper = dataTypeTableHelper.startGroup(name)) {
                final InnerAttributeVisitorImpl nestedAttributeVisitor = new InnerAttributeVisitorImpl(groupHelper, context);
                for (final Attributes.AttributeOrAttributeGroup attributeOrAttributeGroup : attributes.values()) {
                    attributeOrAttributeGroup.accept(nestedAttributeVisitor);
                }

                if (noTlWildcard && null != anyAttribute) {
                    groupHelper.addWildcard(anyAttribute);
                }
            }
        }
    }

    @RequiredArgsConstructor
    private static class InnerAttributeVisitorImpl implements AttributeVisitor {
        final DataTypeTableHelper.GroupHelper groupHelper;
        final Context context;

        public static void handle(final DataTypeTableHelper tableHelper,
                                  final Context context,
                                  final Attributes attributes) {
            try (final DataTypeTableHelper.GroupHelper attributesHelper = tableHelper.startGroup(new QName(""))) {
                new InnerAttributeVisitorImpl(attributesHelper, context).handle(attributes);
            }
        }

        private void handle(final Attributes attributes) {
            for (final Attributes.AttributeOrAttributeGroup attributeOrAttributeGroup : attributes.getAttributes().values()) {
                attributeOrAttributeGroup.accept(this);
            }
            final Attributes.AnyAttribute anyAttribute = attributes.getAnyAttribute();
            if (null != anyAttribute) {
                groupHelper.addWildcard(anyAttribute);
            }
        }

        @Override
        public void visit(final Attributes.LocalAttribute localAttribute) {
            final boolean required = localAttribute.isRequired();
            final Documentations useDocs = localAttribute.getDocs();
            final Attributes.AttributeDefaultOrFixedValue defaultOrFixedValue = localAttribute.getDefaultOrFixedValue();
            final Attributes.LocalAttributeDeclaration localAttributeDeclaration = localAttribute.getLocalAttributeDeclaration();
            final QName name = localAttributeDeclaration.getName();
            final Documentations declDocs = localAttributeDeclaration.getDocs();
            final QName type = localAttributeDeclaration.getType();

            processAttribute(groupHelper, required, name, type, defaultOrFixedValue, Constants.getDocs(useDocs, declDocs));
        }

        @Override
        public void visit(final Attributes.GlobalAttribute globalAttribute) {
            final boolean required = globalAttribute.isRequired();
            final Documentations useDocs = globalAttribute.getDocs();
            final Attributes.AttributeDefaultOrFixedValue useDefaultOrFixedValue = globalAttribute.getDefaultOrFixedValue();
            final Attributes.GlobalAttributeDeclaration globalAttributeDeclaration = (Attributes.GlobalAttributeDeclaration) context.getConcept(globalAttribute.getGlobalAttributeDeclarationName());
            final QName name = globalAttributeDeclaration.getName();
            final Documentations declDocs = globalAttributeDeclaration.getDocs();
            final QName type = globalAttributeDeclaration.getType();
            final Origin origin = globalAttributeDeclaration.getOrigin();
            final Attributes.AttributeDefaultOrFixedValue declDefaultOrFixedValue = globalAttributeDeclaration.getDefaultOrFixedValue();

            final Attributes.AttributeDefaultOrFixedValue defaultOrFixedValue =
                    Attributes.AttributeDefaultOrFixedValue.NONE != useDefaultOrFixedValue ?
                            useDefaultOrFixedValue : declDefaultOrFixedValue;

            processAttribute(groupHelper, required, name, type, defaultOrFixedValue, Constants.getDocs(useDocs, declDocs));
        }

        @Override
        public void visit(final Attributes.AttributeGroup attributeGroup) {
            final Documentations useDocs = attributeGroup.getDocs();
            final Attributes.GlobalAttributeGroupDeclaration globalAttributeGroupDeclaration = (Attributes.GlobalAttributeGroupDeclaration) context.getConcept(attributeGroup.getAttributeGroupDeclarationName());
            final QName name = globalAttributeGroupDeclaration.getName();
            final Documentations declDocs = globalAttributeGroupDeclaration.getDocs();
            final Origin origin = globalAttributeGroupDeclaration.getOrigin();
            final LinkedHashMap<QName, Attributes.AttributeOrAttributeGroup> attributes = globalAttributeGroupDeclaration.getAttributes();
            final Attributes.AnyAttribute anyAttribute = globalAttributeGroupDeclaration.getAnyAttribute();

            processAttribute(groupHelper, true, name, name, Attributes.AttributeDefaultOrFixedValue.NONE, Constants.getDocs(useDocs, declDocs));
        }
    }
}
