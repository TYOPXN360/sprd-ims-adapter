#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate the standard Android radio HIDL callback classes
(Vp19StdRadioResponse / Vp19StdRadioIndication).

These implement android.hardware.radio.V1_0.IRadioResponse / IRadioIndication
so that IExtRadio.setResponseFunctions() (the standard path used by
IRadio.dial / hangup / callStateChanged) delivers responses to this adapter
instead of being dropped. Every abstract method is overridden as a no-op;
a few call-lifecycle methods log for diagnostics.

The abstract method signatures are extracted by the sibling ExtractRadioApi
helper (reflection over android-all-11.jar); this script consumes that list.

Usage:
  python3 gen_std_radio_callbacks.py <api-list-file> <out-dir>
    api-list-file  text from ExtractRadioApi (see gen_std_radio_api.sh)
    out-dir        output dir for the generated .java files
"""
import os
import sys


def parse_api(path):
    blocks = {}
    current = None
    for line in open(path):
        line = line.strip()
        if line.startswith('CLASS '):
            current = line[6:].strip()
            blocks[current] = []
        elif current and line:
            blocks[current].append(line)
    return blocks


def gen(cls, methods, out_dir, logset):
    short = 'Response' if 'Response' in cls else 'Indication'
    jcls = 'Vp19StdRadio' + short
    stub = 'IRadio' + short + '.Stub'
    lines = ['package com.vp19.sprdims.adapter.prototype;', '',
             f'import android.hardware.radio.V1_0.IRadio{short};',
             'import android.os.RemoteException;', '',
             f'public class {jcls} extends {stub} {{']
    for m in methods:
        mname, rest = m.split('(', 1)
        args, ret = rest.rsplit(')', 1)
        ptypes = [p.strip() for p in args.split(',')] if args.strip() else []
        params = ', '.join(f'{t} p{i}' for i, t in enumerate(ptypes))
        body = ''
        if mname in logset:
            body = f'        android.util.Log.i("Vp19StdRadio", "{jcls}.{mname}");'
        lines.append('    @Override')
        lines.append(f'    public {ret} {mname}({params}) throws RemoteException {{')
        if body:
            lines.append(body)
        lines.append('    }')
        lines.append('')
    lines.append('}')
    with open(os.path.join(out_dir, jcls + '.java'), 'w') as f:
        f.write('\n'.join(lines) + '\n')
    print(f'{jcls}: {len(methods)} methods')


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    api = parse_api(sys.argv[1])
    out_dir = sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    resp_log = {'dialResponse', 'acceptCallResponse', 'hangupConnectionResponse',
                'getImsRegistrationStateResponse', 'rejectCallResponse',
                'conferenceResponse'}
    indi_log = {'callStateChanged', 'radioStateChanged', 'networkStateChanged'}
    gen('android.hardware.radio.V1_0.IRadioResponse',
        api.get('android.hardware.radio.V1_0.IRadioResponse', []), out_dir, resp_log)
    gen('android.hardware.radio.V1_0.IRadioIndication',
        api.get('android.hardware.radio.V1_0.IRadioIndication', []), out_dir, indi_log)


if __name__ == '__main__':
    main()
