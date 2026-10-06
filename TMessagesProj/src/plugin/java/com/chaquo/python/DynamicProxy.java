package com.chaquo.python;

/** @deprecated Internal use in proxy.pxi */
@Deprecated
public interface DynamicProxy extends PyProxy {
    PyObject _chaquopyGetType();
}
