# really to test numeric differentation
# see testPoint.cpp

import math

import unittest
import numpy as np
from gtsam import Point2  # really np.array
from numpy.testing import assert_allclose

from numerical_derivative import numericalDerivative11DoublePoint2

x1 = Point2(0, 0)
x2 = Point2(1, 1)
x3 = Point2(1, 1)
l1 = Point2(1, 0)
l2 = Point2(1, 1)
l3 = Point2(2, 2)
l4 = Point2(1, 3)


def norm_proxy(point: Point2):
    return np.linalg.norm(point)


def norm2(p: Point2, H: list[np.array] = None) -> float:
    """
    p: Point2, which is really just np.array
    H: OptionalJacobian<1,2>, really np.array[] always 2d
    """
    r: float = math.sqrt(p[0] * p[0] + p[1] * p[1])
    if H is not None:
        if abs(r) > 1e-10:
            H[0] = [[p[0] / r, p[1] / r]]
        else:
            H[0] = [[1, 1]]  # really infinity, why 1 ?
    return r


def distance2(p: Point2, q: Point2, H: list[np.array] = None) -> float:
    """
    H1: OptionalJacobian<1, 2>
    H2: OptionalJacobian<1, 2>
    """
    d: Point2 = q - p
    if H is not None:
        hz = np.zeros((1, 2))
        r: float = norm2(d, H)
        H[0] = -hz
        H[1] = hz
        return r
    else:
        return np.linalg.norm(d)


class TestPoint2(unittest.TestCase):
    def test_norm(self):
        p0 = Point2(math.cos(5.0), math.sin(5.0))
        self.assertAlmostEqual(1, np.linalg.norm(p0))
        p1 = Point2(4, 5)
        p2 = Point2(1, 1)
        self.assertAlmostEqual(5, distance2(p1, p2))
        self.assertAlmostEqual(5, np.linalg.norm((p2 - p1)))

        actualH: list[np.array] = [np.zeros((1, 2))]

        # exception, for (0,0) derivative is [Inf,Inf] but we return [1,1]
        actual: float = norm2(x1, actualH)
        self.assertAlmostEqual(0, actual)
        expectedH: np.array = np.array([[1.0, 1.0]])
        assert_allclose(expectedH, actualH[0])

        actual: float = norm2(x2, actualH)
        self.assertAlmostEqual(math.sqrt(2.0), actual)
        expectedH: np.array = numericalDerivative11DoublePoint2(norm_proxy, x2)
        assert_allclose(expectedH, actualH[0])

        # analytical
        expectedH: np.array = np.array([[x2[0] / actual, x2[1] / actual]])
        assert_allclose(expectedH, actualH[0])


if __name__ == "__main__":
    unittest.main()
