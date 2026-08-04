#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate the Spreadtrum HIDL callback smali classes
(Vp19ImsRadioResponse / Vp19ImsRadioIndication).

These callbacks are driven by the factory-generated HIDL Stubs from the
original Android 8 ims.apk (vendor.sprd.hardware.radio.V1_0). Each method:
  * logs its name and arguments (Vp19SprdIms tag),
  * forwards IMS registration signals to SprdImsService$RegistrationBridgeListener,
  * forwards call-state changes to SprdImsService.notifyCallStateChanged(),
  * answers getIMSCurrentCalls by forwarding the raw ArrayList to
    SprdImsService.onImsCurrentCallsRaw().

Usage:
  python3 gen_ims_callbacks.py <smali-dir> <vendor-hidl-smali-dir>
    smali-dir            output dir for the generated .smali files
    vendor-hidl-smali-dir  dir containing IIMSRadioResponse.smali /
                           IIMSRadioIndication.smali (from apktool on ims.apk)
"""
import re
import sys
from pathlib import Path

INT_TYPES = {'I', 'B', 'S', 'Z'}
# Indications that mean "IMS is now registered".
REG_IND = {'IMSBearerEstablished', 'IMSNetworkStateChangedInd',
           'enableIMSResponse', 'getIMSBearerStateResponse',
           'getIMSVoiceCallAvailabilityResponse'}
# The single indication that means "call state changed -> re-query call list".
CALL_IND = {'IMSCallStateChangedInd'}


def parse_params(desc):
    """Parse a DEX type list like 'IILandroid/foo/Bar;Ljava/util/ArrayList;'."""
    types, i = [], 0
    while i < len(desc):
        if desc[i] == 'L':
            end = desc.index(';', i)
            types.append(desc[i:end + 1])
            i = end + 1
        elif desc[i] == '[':
            end = i + 1
            while desc[end] == '[':
                end += 1
            if desc[end] == 'L':
                e2 = desc.index(';', end)
                types.append(desc[i:e2 + 1])
                i = e2 + 1
            else:
                types.append(desc[i:end + 1])
                i = end + 1
        else:
            types.append(desc[i])
            i += 1
    return types


def gen(iface, cls, out_dir, base_dir):
    methods = []
    for line in (base_dir / f'{iface}.smali').read_text().splitlines():
        m = re.match(r'\.method public abstract ([^(]+)\(([^)]*)\)(\S+)', line)
        if not m:
            continue
        name, args, ret = m.groups()
        if name in {'asBinder', 'getDebugInfo', 'getHashChain', 'interfaceChain',
                    'interfaceDescriptor', 'linkToDeath', 'notifySyspropsChanged',
                    'ping', 'setHALInstrumentation', 'unlinkToDeath', 'debug'}:
            continue
        methods.append((name, parse_params(args)))

    lines = ['',
             f'.class public final Lcom/vp19/sprdims/adapter/prototype/{cls};',
             f'.super Lvendor/sprd/hardware/radio/V1_0/{iface}$Stub;', '',
             '.field public static sListener:Lcom/vp19/sprdims/adapter/prototype/SprdImsService$RegistrationBridgeListener;', '',
             '.method public static setListener(Lcom/vp19/sprdims/adapter/prototype/SprdImsService$RegistrationBridgeListener;)V',
             '    .locals 0',
             '    sput-object p0, Lcom/vp19/sprdims/adapter/prototype/' + cls + ';->sListener:Lcom/vp19/sprdims/adapter/prototype/SprdImsService$RegistrationBridgeListener;',
             '    return-void', '.end method', '',
             '.method public constructor <init>()V',
             '    .locals 0',
             '    invoke-direct {p0}, Lvendor/sprd/hardware/radio/V1_0/' + iface + '$Stub;-><init>()V',
             '    return-void', '.end method']

    for name, ptypes in methods:
        argdesc = ''.join(ptypes)
        sig = f'({argdesc})V'
        n = len(ptypes)
        regs = 6 + n
        lines += ['', f'.method public {name}{sig}', f'    .locals {regs}',
                  '    const-string v0, "Vp19SprdIms"',
                  '    new-instance v1, Ljava/lang/StringBuilder;',
                  '    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V',
                  f'    const-string v2, "{cls}.{name}("',
                  '    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;']
        for i, t in enumerate(ptypes):
            pi = i + 1
            if i > 0:
                lines += ['    const-string v2, ", "',
                          '    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;']
            if t in INT_TYPES:
                lines += [f'    move v4, p{pi}',
                          '    invoke-virtual {v1, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;']
            else:
                lines += [f'    invoke-static {{p{pi}}}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;',
                          '    move-result-object v3',
                          '    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;']
        lines += ['    const-string v2, ")"',
                  '    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;',
                  '    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
                  '    move-result-object v3',
                  '    invoke-static {v0, v3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I']
        if name == 'getIMSCurrentCallsResponse':
            # Forward the raw Call list to the service for state parsing.
            lines += ['    invoke-static {p2}, Lcom/vp19/sprdims/adapter/prototype/SprdImsService;->onImsCurrentCallsRaw(Ljava/util/ArrayList;)V']
        if name in CALL_IND:
            # Call state changed: ask the modem for the current call list.
            lines += ['    invoke-static {}, Lcom/vp19/sprdims/adapter/prototype/SprdImsService;->requestImsCurrentCalls()V']
        if name in REG_IND:
            lines += ['    sget-object v5, Lcom/vp19/sprdims/adapter/prototype/' + cls + ';->sListener:Lcom/vp19/sprdims/adapter/prototype/SprdImsService$RegistrationBridgeListener;',
                      '    if-eqz v5, :no_lst',
                      '    invoke-interface {v5}, Lcom/vp19/sprdims/adapter/prototype/SprdImsService$RegistrationBridgeListener;->onImsRegistered()V',
                      '    :no_lst']
        lines += ['    return-void', '.end method']

    (out_dir / f'{cls}.smali').write_text('\n'.join(lines) + '\n')
    print(f'{cls}: {len(methods)} methods')


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    out_dir = Path(sys.argv[1])
    base_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    gen('IIMSRadioResponse', 'Vp19ImsRadioResponse', out_dir, base_dir)
    gen('IIMSRadioIndication', 'Vp19ImsRadioIndication', out_dir, base_dir)


if __name__ == '__main__':
    main()
