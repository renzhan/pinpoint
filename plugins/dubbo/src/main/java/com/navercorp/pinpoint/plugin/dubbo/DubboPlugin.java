package com.navercorp.pinpoint.plugin.dubbo;

import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.instrument.Instrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformTemplate;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformTemplateAware;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;
import com.navercorp.pinpoint.plugin.dubbo.interceptor.DubboConsumerInterceptor;
import com.navercorp.pinpoint.plugin.dubbo.interceptor.DubboProviderInterceptor;

import java.security.ProtectionDomain;

/**
 * @author Jinkai.Ma
 */
public class DubboPlugin implements ProfilerPlugin, TransformTemplateAware {

    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());

    private TransformTemplate transformTemplate;

    @Override
    public void setup(ProfilerPluginSetupContext context) {
        DubboConfiguration config = new DubboConfiguration(context.getConfig());
        if (!config.isDubboEnabled()) {
            logger.info("{} disabled", this.getClass().getSimpleName());
            return;
        }
        logger.info("{} config:{}", this.getClass().getSimpleName(), config);

        this.addApplicationTypeDetector(context, config);
        this.addTransformers();
    }

    private void addTransformers() {
        transformTemplate.transform("com.alibaba.dubbo.rpc.protocol.AbstractInvoker", AbstractInvokerTransform.class);
        transformTemplate.transform("com.alibaba.dubbo.rpc.proxy.AbstractProxyInvoker", AbstractProxyInvokerTransform.class);
    }

    public static class AbstractInvokerTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            InstrumentMethod invokeMethod = target.getDeclaredMethod("invoke", "com.alibaba.dubbo.rpc.Invocation");
            if (invokeMethod != null) {
                invokeMethod.addInterceptor(DubboConsumerInterceptor.class);
            }
            return target.toBytecode();
        }
    }

    public static class AbstractProxyInvokerTransform implements TransformCallback {
        @Override
        public byte[] doInTransform(Instrumentor instrumentor, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
            final InstrumentClass target = instrumentor.getInstrumentClass(loader, className, classfileBuffer);
            InstrumentMethod invokeMethod = target.getDeclaredMethod("invoke", "com.alibaba.dubbo.rpc.Invocation");
            if (invokeMethod != null) {
                invokeMethod.addInterceptor(DubboProviderInterceptor.class);
            }
            return target.toBytecode();
        }
    }

    /**
     * Pinpoint profiler agent uses this detector to find out the service type of current application.
     */
    private void addApplicationTypeDetector(ProfilerPluginSetupContext context, DubboConfiguration config) {
        context.addApplicationTypeDetector(new DubboProviderDetector(config.getDubboBootstrapMains()));
    }

    @Override
    public void setTransformTemplate(TransformTemplate transformTemplate) {
        this.transformTemplate = transformTemplate;
    }
}
