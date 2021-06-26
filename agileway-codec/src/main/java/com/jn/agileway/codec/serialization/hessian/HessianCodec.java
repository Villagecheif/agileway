package com.jn.agileway.codec.serialization.hessian;


import com.jn.agileway.codec.AbstractCodec;
import com.jn.langx.codec.CodecException;

import java.io.IOException;

public class HessianCodec<T> extends AbstractCodec<T> {

    @Override
    public byte[] encode(T o) throws CodecException {
        try {
            return Hessians.serialize(o);
        } catch (IOException ex) {
            throw new CodecException(ex.getMessage(), ex);
        }
    }

    @Override
    public T decode(byte[] bytes) throws CodecException {
        try {
            return Hessians.<T>deserialize(bytes, getTargetType());
        } catch (IOException ex) {
            throw new CodecException(ex.getMessage(), ex);
        }
    }

    @Override
    public T decode(byte[] bytes, Class<T> targetType) throws CodecException {
        try {
            return Hessians.deserialize(bytes, targetType);
        } catch (IOException ex) {
            throw new CodecException(ex.getMessage(), ex);
        }
    }


}
