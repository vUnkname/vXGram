package com.chaquo.python;

/** @deprecated Internal use in class.pxi */
@Deprecated
public interface PyProxy {
    PyObject _chaquopyGetDict();
    void _chaquopySetDict(PyObject dict);
}
