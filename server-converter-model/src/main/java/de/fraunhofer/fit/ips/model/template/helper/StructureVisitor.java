package de.fraunhofer.fit.ips.model.template.helper;

import de.fraunhofer.fit.ips.Visitor;
import de.fraunhofer.fit.ips.model.template.Assertion;
import de.fraunhofer.fit.ips.model.template.Function;
import de.fraunhofer.fit.ips.model.template.Level;
import de.fraunhofer.fit.ips.model.template.Particle;
import de.fraunhofer.fit.ips.model.template.Project;
import de.fraunhofer.fit.ips.model.template.Request;
import de.fraunhofer.fit.ips.model.template.Response;
import de.fraunhofer.fit.ips.model.template.Service;
import de.fraunhofer.fit.ips.model.template.Text;

/**
 * @author Fabian Ohler <fabian.ohler1@rwth-aachen.de>
 */
public interface StructureVisitor extends Visitor {
    void visit(final Assertion assertion);

    void visit(final Particle datatype);

    void visit(final Function function);

    void visit(final Level level);

    void visit(final Project project);

    void visit(final Request request);

    void visit(final Response response);

    void visit(final Service service);

    void visit(final Text text);
}
