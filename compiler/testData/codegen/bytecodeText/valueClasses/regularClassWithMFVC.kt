// CHECK_BYTECODE_LISTING
// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses

@JvmInline
value class DPoint(val x: Double, val y: Double) {
    val opposite: DPoint
        get() = DPoint(-x, -y)
}

class DSegment(var p1: DPoint, var p2: DPoint) {

    val center: DPoint
        get() = DPoint(p1.x / 2 + p2.x / 2, p1.y / 2 + p2.y / 2)
    
    var notImplemented: DPoint
        get() = TODO()
        set(_) = TODO()
    
    var point1WithBackingFieldAndDefaultGetter: DPoint = p1
    var point2WithBackingFieldAndDefaultGetter: DPoint = p1
        get() = field
        set(value) {
            field = value
            require("${2 + 2}" == "4")
        }
    
    var pointWithBackingFieldAndCustomGetter: DPoint = p1
        get() = field.also { require("${2 + 2}" == "4") }
        set(value) {
            field = value
            require("${2 + 2}" == "4")
        }
    
    init {
        p1 = p1
    }
}

fun tryGetSegment(segment: DSegment) {
    segment.p1
    segment.p1.x
    segment.p1.y
    segment.p2
    segment.p2.x
    segment.p2.y
    segment.center
    segment.center.x
    segment.center.y
    segment.notImplemented
    segment.notImplemented.x
    segment.notImplemented.y
    segment.point1WithBackingFieldAndDefaultGetter
    segment.point1WithBackingFieldAndDefaultGetter.x
    segment.point1WithBackingFieldAndDefaultGetter.y
    segment.point2WithBackingFieldAndDefaultGetter
    segment.point2WithBackingFieldAndDefaultGetter.x
    segment.point2WithBackingFieldAndDefaultGetter.y
    segment.pointWithBackingFieldAndCustomGetter
    segment.pointWithBackingFieldAndCustomGetter.x
    segment.pointWithBackingFieldAndCustomGetter.y
}

fun trySetSegment(segment: DSegment) {
    segment.notImplemented = segment.p1
    segment.point1WithBackingFieldAndDefaultGetter = segment.p1
    segment.point2WithBackingFieldAndDefaultGetter = segment.p1
    segment.pointWithBackingFieldAndCustomGetter = segment.p1
    segment.p1 = segment.p1
    segment.p2 = segment.p2
}

// 0 public final getOpposite\(\)LDPoint;
// 1 public final static getOpposite-impl\(DD\)LDPoint;
// 1 private D p1\$x
// 1 private D p1\$y
// 0 private DPoint; p1
// 1 private D p2\$x
// 1 private D p2\$y
// 0 private DPoint; p2
// 1 private D point1WithBackingFieldAndDefaultGetter\$x
// 1 private D point1WithBackingFieldAndDefaultGetter\$y
// 0 private DPoint; point1WithBackingFieldAndDefaultGetter
// 1 private D point2WithBackingFieldAndDefaultGetter\$x
// 1 private D point2WithBackingFieldAndDefaultGetter\$y
// 0 private DPoint; point2WithBackingFieldAndDefaultGetter
// 1 private D pointWithBackingFieldAndCustomGetter\$x
// 1 private D pointWithBackingFieldAndCustomGetter\$y
// 0 private DPoint; pointWithBackingFieldAndCustomGetter
// 0 private DPoint; notImplemented
// 0 private D notImplemented
// 0 private DPoint; center
// 0 private D center
// 1 public <init>\(DDDD\)V
// 0 public <init>\(DDDD\)V.*(\n {3}.*)*(\n {4}.*box)
// 1 public final getP1\(\)LDPoint;
// 1 public final setP1-sUp7gFk\(DD\)V
// 1 public final getP2\(\)LDPoint;
// 1 public final setP2-sUp7gFk\(DD\)V
// 1 public final getCenter\(\)LDPoint;
// 1 public final getNotImplemented\(\)LDPoint;
// 1 public final setNotImplemented-sUp7gFk\(DD\)V
// 1 public final getPoint1WithBackingFieldAndDefaultGetter\(\)LDPoint;
// 1 public final setPoint1WithBackingFieldAndDefaultGetter-sUp7gFk\(DD\)V
// 1 public final getPoint2WithBackingFieldAndDefaultGetter\(\)LDPoint;
// 1 public final setPoint2WithBackingFieldAndDefaultGetter-sUp7gFk\(DD\)V
// 1 public final getPointWithBackingFieldAndCustomGetter\(\)LDPoint;
// 1 public final setPointWithBackingFieldAndCustomGetter-sUp7gFk\(DD\)V
// 1 public final getP1\$x\(\)D
// 1 public final getP1\$y\(\)D
// 1 public final getP2\$x\(\)D
// 1 public final getP2\$y\(\)D
// 1 public final getPoint1WithBackingFieldAndDefaultGetter\$x\(\)D
// 1 public final getPoint1WithBackingFieldAndDefaultGetter\$y\(\)D
// 1 public final getPoint2WithBackingFieldAndDefaultGetter\$x\(\)D
// 1 public final getPoint2WithBackingFieldAndDefaultGetter\$y\(\)D
// 0 public final getCenter\$
// 0 public final getNotImplemented\$
// 0 public final getPointWithBackingFieldAndCustomGetter\$
// 0 ^ {2}\b.*get.*\$.*(\n {3}.*)*(\n {4}.*\.box)
// 1 tryGetSegment\(LDSegment;\)V
// 0 try[GS]etSegment\(LDSegment;\)V.*(\n {3}.*)*(\n {4}.*\.box)
// 1 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}.*LDPoint;)){13}
// 0 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}.*LDPoint;)){14}
// 0 trySetSegment\(LDSegment;\)V.*(\n {3}.*)*(\n {4}.*LDPoint;)
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setNotImplemented-sUp7gFk \(DD\)V)){1}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setNotImplemented-sUp7gFk \(DD\)V)){2}
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setPoint1WithBackingFieldAndDefaultGetter-sUp7gFk \(DD\)V)){1}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setPoint1WithBackingFieldAndDefaultGetter-sUp7gFk \(DD\)V)){2}
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setPoint2WithBackingFieldAndDefaultGetter-sUp7gFk \(DD\)V)){1}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setPoint2WithBackingFieldAndDefaultGetter-sUp7gFk \(DD\)V)){2}
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setPointWithBackingFieldAndCustomGetter-sUp7gFk \(DD\)V)){1}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setPointWithBackingFieldAndCustomGetter-sUp7gFk \(DD\)V)){2}
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setP1-sUp7gFk \(DD\)V)){1}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setP1-sUp7gFk \(DD\)V)){2}
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setP2-sUp7gFk \(DD\)V)){1}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DSegment\.setP2-sUp7gFk \(DD\)V)){2}
// 1 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DPoint\.getX \(\)D)){3}
// 0 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DPoint\.getX \(\)D)){4}
// 0 trySetSegment\(LDSegment;\)V.*(\n {3}.*)*(\n {4}INVOKEVIRTUAL DPoint\.getX \(\)D)
// 1 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DPoint\.getY \(\)D)){3}
// 0 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*(\n {4}INVOKEVIRTUAL DPoint\.getY \(\)D)){4}
// 0 trySetSegment\(LDSegment;\)V.*(\n {3}.*)*(\n {4}INVOKEVIRTUAL DPoint\.getY \(\)D)
// 1 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*?(\n {4}INVOKEVIRTUAL DSegment\.get.*\$[xy] \(\)D)){8}
// 0 tryGetSegment\(LDSegment;\)V.*((\n {3}.*)*?(\n {4}INVOKEVIRTUAL DSegment\.get.*\$[xy] \(\)D)){9}
// 1 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*?(\n {4}INVOKEVIRTUAL DSegment\.get.*\$[xy] \(\)D)){12}
// 0 trySetSegment\(LDSegment;\)V.*((\n {3}.*)*?(\n {4}INVOKEVIRTUAL DSegment\.get.*\$[xy] \(\)D)){13}
