''' to O3PipeView.py
Convert debug log to O3PipeView log.

An O3PipeView log of a instruction is a series of continuous logs shown below

```
O3PipeView:fetch:<clock>:<pc>:<rob index>:<seq num>:<disasm>
O3PipeView:decode:<clock>
O3PipeView:rename:<clock>
O3PipeView:dispatch:<clock>
O3PipeView:issue:<clock>
O3PipeView:complete:<clock>
O3PipeView:retire:<clock>:store:0
```

our CPU debug log

``` log
<clock>:PipeView:fetch:<pc>
<clock>:PipeView:decode:<pc>:<binary>
<clock>:PipeView:rename:<pc>
<clock>:PipeView:dispatch:<pc>:<rob index>
<clock>:PipeView:issue:<pc>:<rob index>
<clock>:PipeView:complete:<pc>:<rob index>
<clock>:PipeView:retire:<pc>:<rob index>
<clock>:PipeView:flush:<pc>:<rob index>
``` log

'''

import argparse
from functools import reduce
from sys import stderr

try:
    from capstone import *
    hasCapstone = True
except ModuleNotFoundError:
    print("module capstone not found, not disasm ", file=stderr)
    hasCapstone = False

if hasCapstone:
    md = Cs(CS_ARCH_MIPS, CS_MODE_32)

parser = argparse.ArgumentParser()
parser.add_argument('-f', help="input log file", default="vivado.log")
parser.add_argument('-s', help="start clock", default="1")


class Instruction:
    def __init__(self, num: int, pc: int):
        self.pc = pc
        self.binary = 0

        self.num = num

        self.fetch = 0
        self.decode = 0
        self.rename = 0
        self.dispatch = 0
        self.robIndex = -1
        self.issue = 0
        self.complete = 0
        self.retire = 256

    def flush(self, clock: int) -> str:
        if(self.decode == 0):
            self.decode = clock
        elif(self.rename == 0):
            self.rename = clock
        elif(self.dispatch == 0):
            self.dispatch = clock
        elif(self.issue == 0):
            self.issue = clock
        elif(self.complete == 0):
            self.complete = clock

        self.retire = 0
        return str(self)

    def isDispatched(self) -> bool:
        return self.robIndex >= 0

    def __str__(self) -> str:
        if hasCapstone:
            try:
                disasm = next(md.disasm_lite(
                    self.binary.to_bytes(4, 'little'), 0))
                op = disasm[2]
                args = disasm[3]
            except StopIteration:
                op = hex(self.binary)
                args = ''
                print(
                    f"failed to disasm {self.binary} at {self.pc}", file=stderr)
        else:
            op = hex(self.binary)
            args = ''

        return \
            f"O3PipeView:fetch:{self.fetch}:{hex(self.pc)}:{self.robIndex}:{self.num}:{op} {args}\n\
O3PipeView:decode:{self.decode}\n\
O3PipeView:rename:{self.rename}\n\
O3PipeView:dispatch:{self.dispatch}\n\
O3PipeView:issue:{self.issue}\n\
O3PipeView:complete:{self.complete}\n\
O3PipeView:retire:{self.retire}:store:0\n"


class InstructionList:
    def __init__(self):
        self.insList: list[Instruction] = []

    def setFetch(self, clock: int, ins: Instruction):
        ins.fetch = clock
        self.insList.append(ins)

    def setDecode(self, clock: int, binary: int):
        for ins in self.insList:
            if (ins.decode == 0):
                ins.binary = binary
                ins.decode = clock
                return
        print(f"failed find deocde")

    def setRename(self, clock: int):
        for ins in self.insList:
            if (ins.rename == 0):
                ins.rename = clock
                return
        print(f"failed find rename")

    def setDispatch(self, clock: int, robIndex: int):
        for ins in self.insList:
            if (ins.dispatch == 0):
                ins.dispatch = clock
                ins.robIndex = robIndex
                return
        print(f"failed find dispatch")

    def setIssue(self, clock: int, robIndex: int):
        for ins in self.insList:
            if (ins.robIndex == robIndex):
                ins.issue = clock
                return
        print(f"failed find issue")

    def setComplete(self, clock: int, robIndex: int):
        for ins in self.insList:
            if (ins.robIndex == robIndex):
                ins.complete = clock
                return
        print(f"failed find complete")

    def setRetire(self, clock: int, robIndex: int) -> str:
        for ins in self.insList:
            if (ins.robIndex == robIndex):
                ins.retire = clock
                self.insList.remove(ins)
                return str(ins)
        print(f"failed find retire")
        return ''

    def setFlush(self, clock: int, robIndex: int) -> str:
        if(robIndex >= 0):
            for ins in self.insList:
                if (ins.robIndex == robIndex):
                    ins.flush(clock)
                    self.insList.remove(ins)
                    return str(ins)
        else:
            for ins in self.insList:
                if not ins.isDispatched():
                    ins.flush(clock)
                    self.insList.remove(ins)
                    return str(ins)
        print(f"failed find flush")
        return ''

    def flush(self, clock: int) -> str:
        flushIns = list(filter(lambda x: x.fetch <= clock, self.insList))

        self.insList = list(filter(lambda x: not x in flushIns, self.insList))

        s = reduce(lambda a, b: a+b,
                   map(lambda x: x.flush(clock + 1), flushIns), "")

        return s


def readPipeViewLines(filename: str, startClock: int):
    lines = open(filename).readlines()
    lines = filter(lambda l: "PipeView" in l, lines)
    lines = list(map(lambda l: l.strip().split(":"), lines))

    start = 0
    for i, l in enumerate(lines):
        clock = int(l[0])
        if (clock >= startClock):
            start = i
            break
    lines = lines[start:]

    return lines


if __name__ == "__main__":
    args = parser.parse_args()

    startClock = int(args.s)
    lines = readPipeViewLines(args.f, startClock)

    insMap = {}
    insSeqNum: int = 0

    oneCycleFlush = 0
    oneCycleFlushClock = 0
    globalClock = 0
    for i, l in enumerate(lines):
        clock = int(l[0])
        op = l[2]

        if op == "oneCycleFlush":
            oneCycleFlushClock = clock
            oneCycleFlush = 1
            continue

        pc = int(l[3], 16)

        globalClock = clock

        # flush all instructions encountered
        # todo: multi cycle flush
        if oneCycleFlush == 1 and oneCycleFlushClock + 1 == clock:
            print(f"flush at {oneCycleFlushClock}")
            for k, v in insMap.items():
                print(v.flush(oneCycleFlushClock), end='')
            oneCycleFlush = 0
        if not pc in insMap:
            insMap[pc] = InstructionList()

        if op == "fetch":
            ins = Instruction(insSeqNum, pc)  # new instruction
            insSeqNum = insSeqNum + 1

            insMap[pc].setFetch(clock, ins)
        elif op == "decode":
            binary = int(l[4], 16)
            insMap[pc].setDecode(clock, binary)
        elif op == "rename":
            insMap[pc].setRename(clock)
        elif op == "dispatch":
            robIndex = int(l[4], 16)
            insMap[pc].setDispatch(clock, robIndex)
        elif op == "issue":
            robIndex = int(l[4], 16)
            insMap[pc].setIssue(clock, robIndex)
        elif op == "complete":
            robIndex = int(l[4], 16)
            insMap[pc].setComplete(clock, robIndex)
        elif op == "retire":
            robIndex = int(l[4], 16)
            print(insMap[pc].setRetire(clock, robIndex), end='')
        elif op == "flush":
            if(len(l) >= 5):  # if it has rob index
                robIndex = int(l[4], 16)
            else:
                robIndex = -1
            print(insMap[pc].setFlush(clock, robIndex), end='')

    # flush all
    for k, v in insMap.items():
        print(v.flush(globalClock + 1), end='')
