package de.fraunhofer.fit.ips.testmonitor.routing.messagebased;

import de.fraunhofer.fit.ips.testmonitor.exception.WrappingMonitorException;
import de.fraunhofer.fit.ips.testmonitor.reporting.Reporter;
import de.fraunhofer.fit.ips.testmonitor.routing.FunctionlessExchangeHandler;
import de.fraunhofer.fit.ips.testmonitor.validation.FunctionValidator;
import de.fraunhofer.fit.ips.testmonitor.validation.InstanceValidator;
import de.fraunhofer.fit.ips.testmonitor.routing.ExchangeHandler;
import de.fraunhofer.fit.ips.testmonitor.routing.ExchangeHandlerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Message;

import javax.annotation.Nonnull;
import javax.xml.namespace.QName;
import java.util.HashMap;

/**
 * @author Fabian Ohler <fabian.ohler1@rwth-aachen.de>
 */
@RequiredArgsConstructor
public class MessageBasedExchangeHandlerFactory implements ExchangeHandlerFactory {
    @Nonnull protected final InstanceValidator dataTypeValidator;
    @Nonnull protected final FunctionValidator functionValidator;
    @Nonnull protected final HashMap<QName, MessageBasedFunctionInfo> lookup;

    @Override
    public ExchangeHandler identifyIntent(final Reporter reporter, final Message message)
            throws WrappingMonitorException {
        final QName requestName = ExchangeHandler.determineRootNodeQName(message, this);
        final MessageBasedFunctionInfo functionInfo = lookup.get(requestName);

        if (null == functionInfo) {
            reporter.processMissingFunctionInfo(message, requestName.toString());
            return new FunctionlessExchangeHandler(dataTypeValidator, this);
        }
        reporter.processFunctionIdentified(message, functionInfo);
        return new MessageBasedExchangeHandler(dataTypeValidator, functionValidator, functionInfo, this);
    }
}
